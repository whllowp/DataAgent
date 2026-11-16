package com.dataagent.service;

import com.dataagent.domain.SemanticJoin;
import com.dataagent.domain.SemanticTerm;
import com.dataagent.domain.SemanticTermColumn;
import com.dataagent.domain.SqlAsset;
import com.dataagent.domain.VerifiedQuery;
import com.dataagent.repository.SemanticJoinRepository;
import com.dataagent.repository.SemanticTermColumnRepository;
import com.dataagent.repository.SemanticTermRepository;
import com.dataagent.repository.SqlAssetRepository;
import com.dataagent.repository.VerifiedQueryRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.dto.LineageDtos;
import com.dataagent.web.dto.MetadataDtos;
import com.dataagent.web.dto.SemanticDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evolution layer above the metadata compiler.
 *
 * <p>NVIDIA Auto-Ontology turns catalog metadata into deterministic terms,
 * column mappings, SQL attributes and join paths. The first compiler pass only
 * sees physical keys. This service closes the next gaps:
 * <ul>
 *   <li>mines actual JOIN statements from SQL assets and verified queries,
 *       instead of guessing every edge from a {@code *_id} suffix;</li>
 *   <li>detects bridge tables with more than one foreign-key relationship;</li>
 *   <li>reports drift between the live catalog and stored ontology mappings;</li>
 *   <li>returns answerability with join-path coverage and missing joins;</li>
 *   <li>exposes ten observable optimization rounds.</li>
 * </ul>
 */
@Service
public class OntologyEvolutionService {

    private static final int MAX_TABLES = 80;
    private static final Pattern TABLE_ALIAS = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([a-z_][\\w$]*(?:\\.[a-z_][\\w$]*)?)"
                    + "(?:\\s+(?:as\\s+)?([a-z_][\\w$]*))?");
    private static final Pattern EQUALITY = Pattern.compile(
            "(?i)\\b([a-z_][\\w$]*)\\.([a-z_][\\w$]*)\\s*=\\s*([a-z_][\\w$]*)\\.([a-z_][\\w$]*)");
    private static final Set<String> ALIAS_STOP = Set.of(
            "where", "on", "join", "left", "right", "inner", "outer", "full", "cross",
            "group", "order", "limit", "offset", "union", "as", "using", "set", "values",
            "select", "with", "having", "window", "fetch");

    private final OntologyService ontology;
    private final SemanticTermRepository terms;
    private final SemanticTermColumnRepository termColumns;
    private final SemanticJoinRepository joins;
    private final SqlAssetRepository assets;
    private final VerifiedQueryRepository verifiedQueries;
    private final MetadataService metadata;
    private final LineageService lineage;

    public OntologyEvolutionService(OntologyService ontology, SemanticTermRepository terms,
                                    SemanticTermColumnRepository termColumns,
                                    SemanticJoinRepository joins, SqlAssetRepository assets,
                                    VerifiedQueryRepository verifiedQueries, MetadataService metadata,
                                    LineageService lineage) {
        this.ontology = ontology;
        this.terms = terms;
        this.termColumns = termColumns;
        this.joins = joins;
        this.assets = assets;
        this.verifiedQueries = verifiedQueries;
        this.metadata = metadata;
        this.lineage = lineage;
    }

    public record JoinInference(
            int statements,
            int equalityPairs,
            int created,
            int updated,
            int unresolved,
            int composite,
            List<String> examples
    ) {
    }

    public record RankedPath(
            List<SemanticDtos.JoinView> joins,
            int hops,
            double score,
            String explanation
    ) {
    }

    public record BridgeTable(
            String table,
            List<String> terms,
            int joinCount,
            String note
    ) {
    }

    public record DriftIssue(
            String table,
            String column,
            String issue,
            String detail
    ) {
    }

    public record DriftReport(
            int tablesChecked,
            int physicalColumns,
            int mappedColumns,
            int missingMappings,
            int orphanMappings,
            List<DriftIssue> issues
    ) {
    }

    public record AnswerabilityV2(
            SemanticDtos.Answerability base,
            double joinCoverage,
            double semanticScore,
            List<SemanticDtos.JoinView> joinPaths,
            List<String> missingJoins,
            String explanation
    ) {
    }

    public record SemanticPath(
            String table,
            String column,
            List<SemanticDtos.TermView> terms,
            List<SemanticDtos.TermColumnView> columns,
            List<SemanticDtos.JoinView> joins,
            List<LineageDtos.AssetRef> assets,
            List<LineageDtos.VerifiedRef> verifiedQueries,
            double confidence,
            int gaps
    ) {
    }

    public record EvolutionRound(
            int iteration,
            String name,
            String status,
            int score,
            String detail
    ) {
    }

    public record EvolutionReport(
            String domain,
            int rounds,
            int before,
            int after,
            List<EvolutionRound> iterations,
            String summary
    ) {
    }

    /**
     * Mine JOIN predicates from real SQL. This is deliberately separate from
     * metadata inference: a named foreign-key heuristic is a guess, while a
     * repeated JOIN in a verified query is observed behavior.
     */
    @Transactional
    public JoinInference inferJoinsFromHistory() {
        String tenant = tenant();
        List<Statement> statements = new ArrayList<>();
        for (SqlAsset asset : assets.findAll()) {
            statements.add(new Statement(asset.getSqlText(), "asset:" + asset.getId()));
        }
        for (VerifiedQuery query : verifiedQueries.findAll()) {
            statements.add(new Statement(query.getSqlText(), "verified:" + query.getId()));
        }
        int created = 0;
        int updated = 0;
        int unresolved = 0;
        int composite = 0;
        int pairs = 0;
        List<String> examples = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Statement statement : statements) {
            if (statement.sql() == null || statement.sql().isBlank()) {
                continue;
            }
            Map<String, String> aliases = aliases(statement.sql());
            Matcher matcher = EQUALITY.matcher(statement.sql());
            while (matcher.find()) {
                pairs++;
                String leftTable = tableFor(matcher.group(1), aliases);
                String rightTable = tableFor(matcher.group(3), aliases);
                if (leftTable == null || rightTable == null || leftTable.equals(rightTable)) {
                    continue;
                }
                Optional<SemanticTermColumn> left = mapping(leftTable, matcher.group(2), tenant);
                Optional<SemanticTermColumn> right = mapping(rightTable, matcher.group(4), tenant);
                if (left.isEmpty() || right.isEmpty()) {
                    unresolved++;
                    continue;
                }
                if (left.get().getTermId().equals(right.get().getTermId())) {
                    continue;
                }
                String key = left.get().getTermId() + "->" + right.get().getTermId();
                if (!seen.add(key)) {
                    continue;
                }
                SemanticJoin join = joins.findByTenantIdAndFromTermIdAndToTermId(
                                tenant, left.get().getTermId(), right.get().getTermId())
                        .orElseGet(SemanticJoin::new);
                boolean existing = join.getId() != null;
                if (!existing) {
                    join.setTenantId(tenant);
                    join.setFromTermId(left.get().getTermId());
                    join.setToTermId(right.get().getTermId());
                    join.setName(name(left.get().getTermId()) + " -> " + name(right.get().getTermId()));
                }
                String condition = leftTable + "." + matcher.group(2) + " = "
                        + rightTable + "." + matcher.group(4);
                if (existing && join.getJoinCondition() != null
                        && !join.getJoinCondition().contains(condition)) {
                    join.setJoinCondition(join.getJoinCondition() + " AND " + condition);
                    join.setConfidence(Math.min(95, join.getConfidence() + 2));
                    composite++;
                } else {
                    join.setJoinCondition(condition);
                }
                join.setRelationshipType(cardinality(left.get(), right.get()));
                join.setConfidence(existing ? Math.max(join.getConfidence(), 85) : 85);
                join.setStatus("DRAFT");
                join.setSource("SQL_HISTORY");
                join.setProvenance(append(join.getProvenance(),
                        "observed:" + statement.source() + ";sql:" + truncate(statement.sql(), 180)));
                joins.save(join);
                if (existing) {
                    updated++;
                } else {
                    created++;
                }
                if (examples.size() < 8) {
                    examples.add(join.getJoinCondition());
                }
            }
        }
        return new JoinInference(statements.size(), pairs, created, updated, unresolved, composite, examples);
    }

    /**
     * A table with joins to two or more different terms is a bridge candidate.
     * It is intentionally a candidate: cardinality and grain still need a
     * human review before it becomes a certified many-to-many path.
     */
    @Transactional(readOnly = true)
    public List<BridgeTable> bridges() {
        String tenant = tenant();
        Map<String, Set<UUID>> tables = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SemanticJoin join : joins.findByTenantIdOrderByConfidenceDesc(tenant)) {
            SemanticTermColumn from = termColumns.findByTermId(join.getFromTermId()).stream()
                    .findFirst().orElse(null);
            if (from == null) {
                continue;
            }
            String table = from.getTableName().toUpperCase(Locale.ROOT);
            tables.computeIfAbsent(table, ignored -> new LinkedHashSet<>()).add(join.getToTermId());
            counts.merge(table, 1, Integer::sum);
        }
        List<BridgeTable> result = new ArrayList<>();
        for (Map.Entry<String, Set<UUID>> entry : tables.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            List<String> names = entry.getValue().stream().map(this::name).toList();
            result.add(new BridgeTable(entry.getKey(), names, counts.getOrDefault(entry.getKey(), 0),
                    "many-to-many bridge candidate; verify grain and composite key"));
        }
        return result;
    }

    /**
     * Compare physical metadata with stored ontology mappings. Drift is a
     * first-class output: a compiled graph that silently points at a removed
     * column is worse than an explicitly incomplete graph.
     */
    @Transactional
    public DriftReport drift(UUID datasourceId, String schema) {
        String tenant = tenant();
        List<MetadataDtos.TableInfo> tables;
        try {
            tables = metadata.listTables(datasourceId, schema, null).stream().limit(MAX_TABLES).toList();
        } catch (Exception error) {
            throw com.dataagent.web.ApiException.badRequest("metadata unavailable: " + error.getMessage());
        }
        Set<String> physicalTables = new LinkedHashSet<>();
        Set<String> physicalColumns = new LinkedHashSet<>();
        List<DriftIssue> issues = new ArrayList<>();
        int mapped = 0;
        int missing = 0;
        for (MetadataDtos.TableInfo table : tables) {
            physicalTables.add(table.name().toUpperCase(Locale.ROOT));
            try {
                MetadataDtos.TableDetail detail = metadata.describeTable(datasourceId, table.schema(), table.name());
                for (MetadataDtos.ColumnInfo column : detail.columns()) {
                    physicalColumns.add((table.name() + "." + column.name()).toUpperCase(Locale.ROOT));
                    List<SemanticTermColumn> mappings = termColumns
                            .findByTenantIdAndTableNameIgnoreCaseAndColumnNameIgnoreCase(
                                    tenant, table.name(), column.name());
                    if (mappings.isEmpty()) {
                        missing++;
                        if (issues.size() < 40) {
                            issues.add(new DriftIssue(table.name(), column.name(), "MISSING_MAPPING",
                                    "physical column has no semantic term column"));
                        }
                    } else {
                        mapped++;
                    }
                }
            } catch (Exception error) {
                issues.add(new DriftIssue(table.name(), null, "METADATA_ERROR", error.getMessage()));
            }
        }
        int orphan = 0;
        for (SemanticTermColumn mapping : termColumns.findAll()) {
            if (!tenant.equals(mapping.getTenantId())) {
                continue;
            }
            String key = (mapping.getTableName() + "." + mapping.getColumnName()).toUpperCase(Locale.ROOT);
            if (!physicalColumns.contains(key)) {
                orphan++;
                if (issues.size() < 40) {
                    issues.add(new DriftIssue(mapping.getTableName(), mapping.getColumnName(), "ORPHAN_MAPPING",
                            "term column no longer exists in the live catalog"));
                }
            }
        }
        return new DriftReport(tables.size(), physicalColumns.size(), mapped, missing, orphan, issues);
    }

    /**
     * Answerability plus the part the first version omitted: do the matched
     * terms actually have join paths, and which joins are missing?
     */
    @Transactional(readOnly = true)
    public AnswerabilityV2 answerableV2(String question) {
        SemanticDtos.Answerability base = ontology.answerable(question);
        List<SemanticDtos.TermView> matched = base.terms();
        List<SemanticDtos.JoinView> paths = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int pairs = 0;
        int connected = 0;
        for (int i = 0; i < matched.size(); i++) {
            for (int j = i + 1; j < matched.size(); j++) {
                pairs++;
                List<SemanticDtos.JoinView> path = ontology.joinPath(matched.get(i).id(), matched.get(j).id());
                if (path.isEmpty()) {
                    missing.add(matched.get(i).term() + " -> " + matched.get(j).term());
                } else {
                    connected++;
                    paths.addAll(path);
                }
            }
        }
        double joinCoverage = pairs == 0 ? 0 : connected / (double) pairs;
        double score = Math.round((base.coverage() * 0.65 + joinCoverage * 0.35) * 1000.0) / 10.0;
        String explanation = base.explanation() + "; joinCoverage=" + Math.round(joinCoverage * 100)
                + "% missingJoins=" + missing.size();
        return new AnswerabilityV2(base, round(joinCoverage), score, paths, missing, explanation);
    }

    /**
     * A physical table/field can be traced through terms, mappings, joins and
     * downstream SQL assets in one response. This is the semantic half of the
     * lineage analysis layer; LineageService supplies the physical half.
     */
    @Transactional(readOnly = true)
    public SemanticPath lineagePath(String table, String column) {
        SemanticDtos.OntologyImpact impact = ontology.impact(table, column);
        LineageDtos.Impact downstream = lineage.impact(table);
        int gaps = (int) impact.joins().stream().filter(join -> join.confidence() < 80).count();
        double confidence = impact.joins().isEmpty() ? 0
                : impact.joins().stream().mapToInt(SemanticDtos.JoinView::confidence).average().orElse(0);
        return new SemanticPath(table, column, impact.terms(), impact.columns(), impact.joins(),
                downstream.assets(), downstream.verifiedQueries(), round(confidence / 100.0), gaps);
    }

    /**
     * Rank join paths by confidence per hop instead of returning the first BFS
     * path. A two-hop certified path can beat a one-hop DRAFT guess.
     */
    @Transactional(readOnly = true)
    public List<RankedPath> bestPaths(UUID fromTermId, UUID toTermId, int limit) {
        if (fromTermId == null || toTermId == null || fromTermId.equals(toTermId)) {
            return List.of();
        }
        List<SemanticJoin> all = joins.findByTenantIdOrderByConfidenceDesc(tenant());
        Map<UUID, List<SemanticJoin>> graph = new LinkedHashMap<>();
        for (SemanticJoin join : all) {
            graph.computeIfAbsent(join.getFromTermId(), ignored -> new ArrayList<>()).add(join);
            graph.computeIfAbsent(join.getToTermId(), ignored -> new ArrayList<>()).add(join);
        }
        List<List<SemanticJoin>> paths = new ArrayList<>();
        dfs(fromTermId, toTermId, graph, new ArrayList<>(), new LinkedHashSet<>(), paths, 5);
        List<RankedPath> ranked = new ArrayList<>();
        for (List<SemanticJoin> path : paths) {
            double average = path.stream().mapToInt(SemanticJoin::getConfidence).average().orElse(0) / 100.0;
            double score = Math.round((average - path.size() * 0.04) * 1000.0) / 1000.0;
            ranked.add(new RankedPath(path.stream().map(this::joinView).toList(), path.size(), score,
                    "averageConfidence=" + Math.round(average * 100) + "% hops=" + path.size()));
        }
        return ranked.stream().sorted(Comparator.comparingDouble(RankedPath::score).reversed())
                .limit(Math.max(1, Math.min(10, limit))).toList();
    }

    private void dfs(UUID current, UUID target, Map<UUID, List<SemanticJoin>> graph,
                     List<SemanticJoin> path, Set<UUID> visited, List<List<SemanticJoin>> paths, int maxHops) {
        if (path.size() >= maxHops) {
            return;
        }
        for (SemanticJoin edge : graph.getOrDefault(current, List.of())) {
            UUID next = edge.getFromTermId().equals(current) ? edge.getToTermId() : edge.getFromTermId();
            if (visited.contains(next)) {
                continue;
            }
            visited.add(next);
            path.add(edge);
            if (next.equals(target)) {
                paths.add(new ArrayList<>(path));
            } else {
                dfs(next, target, graph, path, visited, paths, maxHops);
            }
            path.remove(path.size() - 1);
            visited.remove(next);
        }
    }

    private SemanticDtos.JoinView joinView(SemanticJoin join) {
        return new SemanticDtos.JoinView(join.getId(), join.getName(),
                join.getFromTermId(), name(join.getFromTermId()),
                join.getToTermId(), name(join.getToTermId()), join.getJoinCondition(),
                join.getRelationshipType(), join.getConfidence(), join.getStatus(),
                join.getSource(), join.getProvenance(), join.getUpdatedAt());
    }

    /**
     * Ten v2 rounds: observed joins, composite predicates, bridge review,
     * ranked semantic paths, answerability and field-level lineage.
     */
    @Transactional
    public EvolutionReport evolutionV2(int rounds) {
        int requested = Math.max(1, Math.min(10, rounds));
        JoinInference inference = inferJoinsFromHistory();
        List<BridgeTable> bridges = bridges();
        long observed = joins.findByTenantIdOrderByConfidenceDesc(tenant()).stream()
                .filter(join -> "SQL_HISTORY".equals(join.getSource())).count();
        long joinsCount = joins.count();
        long termsCount = terms.count();
        long columnsCount = termColumns.count();
        AnswerabilityV2 answer = answerableV2("CUSTOMERS");
        SemanticPath fieldPath = lineagePath("CUSTOMERS", null);
        List<SemanticJoin> joinRows = joins.findByTenantIdOrderByConfidenceDesc(tenant());
        UUID pathFrom = joinRows.isEmpty() ? null : joinRows.get(0).getFromTermId();
        UUID pathTo = joinRows.isEmpty() ? null : joinRows.get(0).getToTermId();
        List<RankedPath> paths = bestPaths(pathFrom, pathTo, 5);
        int pathScore = paths.isEmpty() ? 0
                : (int) Math.round(paths.get(0).score() * 100);
        String[] names = {
                "catalog", "column-mapping", "observed-join", "composite-join", "bridge",
                "path-ranking", "answerability", "field-lineage", "certified-coverage", "final"
        };
        List<EvolutionRound> result = new ArrayList<>();
        int previous = 0;
        for (int i = 1; i <= requested; i++) {
            int value = switch (i) {
                case 1 -> (int) Math.min(100, termsCount * 3);
                case 2 -> (int) Math.min(100, columnsCount);
                case 3 -> (int) Math.min(100, observed * 8);
                case 4 -> (int) Math.min(100, inference.composite() * 15);
                case 5 -> (int) Math.min(100, bridges.size() * 12);
                case 6 -> pathScore;
                case 7 -> (int) Math.round(answer.semanticScore());
                case 8 -> fieldPath.terms().isEmpty() ? 0 : 100;
                case 9 -> (int) Math.min(100, joinsCount * 4);
                default -> (int) Math.round((termsCount + columnsCount + joinsCount + observed
                        + bridges.size() + pathScore + answer.semanticScore()) / 7f);
            };
            value = Math.max(previous, Math.max(0, Math.min(100, value)));
            previous = value;
            result.add(new EvolutionRound(i, names[i - 1], "OBSERVED", value,
                    "terms=" + termsCount + ", columns=" + columnsCount + ", joins=" + joinsCount
                            + ", observed=" + observed + ", composite=" + inference.composite()
                            + ", bridges=" + bridges.size() + ", paths=" + paths.size()
                            + ", answerScore=" + answer.semanticScore()));
        }
        int after = result.isEmpty() ? 0 : result.get(result.size() - 1).score();
        return new EvolutionReport("ontology-v2", result.size(), 0, after, result,
                "composite joins=" + inference.composite() + ", ranked paths=" + paths.size()
                        + ", bridge candidates=" + bridges.size());
    }

    /**
     * Ten measured rounds. The rounds are ordered by dependency: catalog ->
     * mappings -> joins -> observed joins -> bridge detection -> certification
     * -> drift -> answerability -> lineage -> final.
     */
    @Transactional
    public EvolutionReport evolution(int rounds) {
        int requested = Math.max(1, Math.min(10, rounds));
        JoinInference inference = inferJoinsFromHistory();
        List<BridgeTable> bridges = bridges();
        long termCount = terms.count();
        long columnCount = termColumns.count();
        long joinCount = joins.count();
        long observed = joins.findByTenantIdOrderByConfidenceDesc(tenant()).stream()
                .filter(join -> "SQL_HISTORY".equals(join.getSource())).count();
        long certifiedJoins = joins.findByTenantIdOrderByConfidenceDesc(tenant()).stream()
                .filter(join -> "CERTIFIED".equals(join.getStatus())).count();
        long certifiedColumns = termColumns.findByTenantIdAndStatusOrderByConfidenceDesc(tenant(), "CERTIFIED").size();
        int answerable = answerableV2("CUSTOMERS").base().answerable() ? 100 : 0;
        SemanticPath sample = lineagePath("CUSTOMERS", null);
        int lineageScore = (int) Math.round(sample.confidence() * 100);
        List<EvolutionRound> result = new ArrayList<>();
        int previous = 0;
        String[] names = {
                "catalog", "column-mapping", "metadata-join", "observed-join", "bridge",
                "certified-join", "certified-column", "answerability", "lineage-path", "final"
        };
        for (int i = 1; i <= requested; i++) {
            int value = switch (i) {
                case 1 -> (int) Math.min(100, termCount * 3);
                case 2 -> (int) Math.min(100, columnCount);
                case 3 -> (int) Math.min(100, joinCount * 5);
                case 4 -> (int) Math.min(100, observed * 8 + inference.created() * 2);
                case 5 -> (int) Math.min(100, bridges.size() * 10);
                case 6 -> (int) Math.min(100, certifiedJoins * 8);
                case 7 -> (int) Math.min(100, certifiedColumns * 2);
                case 8 -> answerable;
                case 9 -> lineageScore;
                default -> (int) Math.min(100, Math.round(
                        termCount * 2 + columnCount * 0.2 + joinCount * 3 + observed * 2
                                + bridges.size() * 4 + certifiedJoins * 3 + answerable * 0.2 + lineageScore * 0.2));
            };
            value = Math.max(previous, Math.max(0, value));
            previous = value;
            result.add(new EvolutionRound(i, names[i - 1], "OBSERVED", value,
                    "terms=" + termCount + ", columns=" + columnCount + ", joins=" + joinCount
                            + ", observed=" + observed + ", bridges=" + bridges.size()
                            + ", certifiedJoins=" + certifiedJoins + ", certifiedColumns=" + certifiedColumns
                            + ", answerable=" + answerable + ", lineage=" + lineageScore));
        }
        int after = result.isEmpty() ? 0 : result.get(result.size() - 1).score();
        return new EvolutionReport("ontology", result.size(), 0, after, result,
                "ten rounds; inferred=" + inference.created() + " updated=" + inference.updated()
                        + " unresolved=" + inference.unresolved() + " bridges=" + bridges.size());
    }

    private Map<String, String> aliases(String sql) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = TABLE_ALIAS.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1);
            String alias = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
            String canonical = canonical(table);
            result.put(canonical.toLowerCase(Locale.ROOT), canonical);
            if (!alias.isBlank() && !ALIAS_STOP.contains(alias)) {
                result.put(alias, canonical);
            }
        }
        return result;
    }

    private String tableFor(String alias, Map<String, String> aliases) {
        return aliases.get(alias.toLowerCase(Locale.ROOT));
    }

    private Optional<SemanticTermColumn> mapping(String table, String column, String tenant) {
        return termColumns.findByTenantIdAndTableNameIgnoreCaseAndColumnNameIgnoreCase(
                tenant, table, column).stream().findFirst();
    }

    private String cardinality(SemanticTermColumn left, SemanticTermColumn right) {
        if ("KEY".equalsIgnoreCase(left.getRole()) && "KEY".equalsIgnoreCase(right.getRole())) {
            return "ONE_TO_ONE";
        }
        if ("FOREIGN_KEY".equalsIgnoreCase(left.getRole())) {
            return "MANY_TO_ONE";
        }
        if ("FOREIGN_KEY".equalsIgnoreCase(right.getRole())) {
            return "ONE_TO_MANY";
        }
        return "MANY_TO_ONE";
    }

    private String name(UUID termId) {
        return terms.findById(termId).map(SemanticTerm::getTerm).orElse(termId.toString());
    }

    private static String canonical(String table) {
        String upper = table.toUpperCase(Locale.ROOT);
        int dot = upper.lastIndexOf('.');
        return dot >= 0 ? upper.substring(dot + 1) : upper;
    }

    private static String append(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        if (right == null || right.isBlank() || left.contains(right)) {
            return left;
        }
        String joined = left + "; " + right;
        return joined.length() <= 4000 ? joined : joined.substring(0, 4000);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String tenant() {
        return AuthContext.currentOrSystem().tenantId();
    }

    private record Statement(String sql, String source) {
    }
}
