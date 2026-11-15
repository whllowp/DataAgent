package com.dataagent.service;

import com.dataagent.domain.SemanticCompileRun;
import com.dataagent.domain.SemanticEntity;
import com.dataagent.domain.SemanticJoin;
import com.dataagent.domain.SemanticMetric;
import com.dataagent.domain.SemanticSqlAttribute;
import com.dataagent.domain.SemanticTerm;
import com.dataagent.domain.SemanticTermColumn;
import com.dataagent.repository.SemanticCompileRunRepository;
import com.dataagent.repository.SemanticEntityRepository;
import com.dataagent.repository.SemanticJoinRepository;
import com.dataagent.repository.SemanticMetricRepository;
import com.dataagent.repository.SemanticSqlAttributeRepository;
import com.dataagent.repository.SemanticTermColumnRepository;
import com.dataagent.repository.SemanticTermRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.MetadataDtos;
import com.dataagent.web.dto.SemanticDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Auto-Ontology style compilation and grounding. Metadata becomes terms with
 * column mappings and inferred joins; reviewed SQL attributes carry reusable
 * business expressions. Answerability is computed before SQL generation.
 */
@Service
public class OntologyService {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "of", "for", "and", "or", "to", "in", "on", "by", "with",
            "what", "which", "who", "how", "many", "much", "is", "are", "was", "were",
            "last", "this", "that", "week", "month", "year", "day", "total", "please",
            "的", "了", "是", "在", "和", "与", "按", "查", "看", "一下", "多少", "最近", "本周",
            "本月", "今年", "请", "帮", "我", "这个", "那个", "数据");

    private final MetadataService metadata;
    private final SemanticEntityRepository entities;
    private final SemanticTermRepository terms;
    private final SemanticMetricRepository metrics;
    private final SemanticTermColumnRepository termColumns;
    private final SemanticSqlAttributeRepository sqlAttributes;
    private final SemanticJoinRepository joins;
    private final SemanticCompileRunRepository compileRuns;
    private final EmbeddingService embeddings;

    public OntologyService(MetadataService metadata, SemanticEntityRepository entities,
                           SemanticTermRepository terms, SemanticMetricRepository metrics,
                           SemanticTermColumnRepository termColumns,
                           SemanticSqlAttributeRepository sqlAttributes,
                           SemanticJoinRepository joins, SemanticCompileRunRepository compileRuns,
                           EmbeddingService embeddings) {
        this.metadata = metadata;
        this.entities = entities;
        this.terms = terms;
        this.metrics = metrics;
        this.termColumns = termColumns;
        this.sqlAttributes = sqlAttributes;
        this.joins = joins;
        this.compileRuns = compileRuns;
        this.embeddings = embeddings;
    }

    @Transactional
    public SemanticDtos.CompileRunView compile(SemanticDtos.CompileRequest request) {
        String tenant = tenant();
        SemanticCompileRun run = new SemanticCompileRun();
        run.setTenantId(tenant);
        run.setDatasourceId(request.datasourceId());
        run.setSchemaName(request.schema());
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run = compileRuns.save(run);
        List<String> warnings = new ArrayList<>();
        Map<String, SemanticTerm> termByTable = new LinkedHashMap<>();
        Map<String, MetadataDtos.TableDetail> tableDetails = new LinkedHashMap<>();
        List<MetadataDtos.TableInfo> tables;
        try {
            tables = metadata.listTables(request.datasourceId(), request.schema(), null);
        } catch (Exception error) {
            throw ApiException.badRequest("读取数据源元数据失败: " + error.getMessage());
        }
        for (MetadataDtos.TableInfo table : tables.stream().limit(80).toList()) {
            try {
                MetadataDtos.TableDetail detail = metadata.describeTable(
                        request.datasourceId(), table.schema(), table.name());
                tableDetails.put(table.name().toUpperCase(Locale.ROOT), detail);
                run.setScannedTables(run.getScannedTables() + 1);
                run.setScannedColumns(run.getScannedColumns() + detail.columns().size());
                SemanticTerm term = upsertTerm(tenant, table, detail);
                termByTable.put(table.name().toUpperCase(Locale.ROOT), term);
                mapColumns(tenant, term, request.datasourceId(), table, detail);
                attachMetricAttributes(tenant, term, request.datasourceId(), table.name());
            } catch (Exception error) {
                warnings.add(table.name() + ": " + error.getMessage());
            }
        }
        int createdJoins = inferJoins(tenant, request.datasourceId(), termByTable, tableDetails);
        run.setCreatedTerms(termByTable.size());
        run.setMappedColumns(run.getScannedColumns());
        run.setCreatedJoins(createdJoins);
        run.setCreatedAttributes((int) sqlAttributes.countByTenantId(tenant));
        run.setStatus("COMPLETED");
        run.setMessage("compiled " + tableDetails.size() + " tables; warnings=" + warnings.size()
                + (warnings.isEmpty() ? "" : " first=" + warnings.get(0)));
        run.setFinishedAt(Instant.now());
        compileRuns.save(run);
        return compileView(run);
    }

    private SemanticTerm upsertTerm(String tenant, MetadataDtos.TableInfo table,
                                    MetadataDtos.TableDetail detail) {
        SemanticEntity entity = entities.findByNameIgnoreCase(table.name()).orElseGet(SemanticEntity::new);
        if (entity.getId() == null) {
            entity.setName(table.name());
            entity.setDisplayName(table.name());
            entity.setDomain(table.schema());
            entity.setDescription(detail.remarks() == null ? "Compiled from " + table.name() : detail.remarks());
            entity.setPhysicalTables(table.schema() == null ? table.name()
                    : table.schema() + "." + table.name());
            entity.setOwner("ontology-compiler");
            entity.setTags("COMPILED");
            entity.setStatus("DRAFT");
            entity = entities.save(entity);
        }
        SemanticTerm term = terms.findByTermIgnoreCase(table.name()).orElseGet(SemanticTerm::new);
        if (term.getId() == null) {
            term.setTerm(table.name());
            term.setDefinition(detail.remarks() == null
                    ? "Compiled term for " + table.name() : detail.remarks());
            term.setSynonyms(table.name());
            term.setRelatedEntityId(entity.getId());
            term.setOwner("ontology-compiler");
            term.setTags("COMPILED," + tenant);
            term.setStatus("DRAFT");
            term = terms.save(term);
        }
        return term;
    }

    private void mapColumns(String tenant, SemanticTerm term, UUID datasourceId,
                            MetadataDtos.TableInfo table, MetadataDtos.TableDetail detail) {
        for (MetadataDtos.ColumnInfo column : detail.columns()) {
            String role = column.primaryKey() ? "KEY"
                    : column.name().toUpperCase(Locale.ROOT).endsWith("_ID") ? "FOREIGN_KEY" : "ATTRIBUTE";
            SemanticTermColumn mapping = termColumns
                    .findByTenantIdAndTermIdAndTableNameIgnoreCaseAndColumnNameIgnoreCase(
                            tenant, term.getId(), table.name(), column.name())
                    .orElseGet(SemanticTermColumn::new);
            mapping.setTenantId(tenant);
            mapping.setTermId(term.getId());
            mapping.setDatasourceId(datasourceId);
            mapping.setSchemaName(table.schema());
            mapping.setTableName(table.name());
            mapping.setColumnName(column.name());
            mapping.setRole(role);
            mapping.setConfidence(column.primaryKey() ? 90 : 70);
            mapping.setStatus("DRAFT");
            mapping.setProvenance("metadata:" + (table.schema() == null ? "" : table.schema() + ".")
                    + table.name() + "." + column.name());
            termColumns.save(mapping);
        }
    }

    private void attachMetricAttributes(String tenant, SemanticTerm term, UUID datasourceId,
                                        String tableName) {
        List<SemanticMetric> related = metrics.findByEntityIdOrderByDisplayNameAsc(term.getRelatedEntityId());
        if (related.isEmpty()) {
            related = metrics.findAll().stream()
                    .filter(metric -> metric.getExpression() != null && !metric.getExpression().isBlank())
                    .filter(metric -> metric.getEntityId() != null)
                    .filter(metric -> entities.findById(metric.getEntityId())
                            .map(entity -> entity.getPhysicalTables() != null
                                    && entity.getPhysicalTables().toUpperCase(Locale.ROOT)
                                    .contains(tableName.toUpperCase(Locale.ROOT)))
                            .orElse(false))
                    .toList();
        }
        for (SemanticMetric metric : related) {
            if (metric.getExpression() == null || metric.getExpression().isBlank()) {
                continue;
            }
            String name = metric.getName();
            boolean exists = sqlAttributes.findByTermIdOrderByStatusAscNameAsc(term.getId()).stream()
                    .anyMatch(attribute -> attribute.getName().equalsIgnoreCase(name));
            if (exists) {
                continue;
            }
            SemanticSqlAttribute attribute = new SemanticSqlAttribute();
            attribute.setTenantId(tenant);
            attribute.setTermId(term.getId());
            attribute.setName(name);
            attribute.setDisplayName(metric.getDisplayName());
            attribute.setExpression(metric.getExpression());
            attribute.setBusinessDefinition(metric.getBusinessDefinition());
            attribute.setGrain(metric.getDimensions());
            attribute.setSynonyms(metric.getSynonyms());
            attribute.setOwner(metric.getOwner());
            attribute.setTags(metric.getTags());
            attribute.setStatus("DRAFT");
            attribute.setConfidence(80);
            attribute.setSource("METRIC");
            attribute.setProvenance("metric:" + metric.getId() + ";table:" + tableName
                    + ";datasource:" + datasourceId);
            sqlAttributes.save(attribute);
        }
    }

    private int inferJoins(String tenant, UUID datasourceId, Map<String, SemanticTerm> termByTable,
                           Map<String, MetadataDtos.TableDetail> tableDetails) {
        int created = 0;
        for (Map.Entry<String, MetadataDtos.TableDetail> entry : tableDetails.entrySet()) {
            SemanticTerm sourceTerm = termByTable.get(entry.getKey());
            if (sourceTerm == null) {
                continue;
            }
            for (MetadataDtos.ColumnInfo column : entry.getValue().columns()) {
                String columnName = column.name().toUpperCase(Locale.ROOT);
                if (!columnName.endsWith("_ID")) {
                    continue;
                }
                String stem = columnName.substring(0, columnName.length() - 3);
                String targetKey = findTargetTable(stem, tableDetails.keySet());
                if (targetKey == null || targetKey.equals(entry.getKey())) {
                    continue;
                }
                SemanticTerm targetTerm = termByTable.get(targetKey);
                if (targetTerm == null) {
                    continue;
                }
                MetadataDtos.TableDetail target = tableDetails.get(targetKey);
                String targetColumn = target == null ? "ID" : target.columns().stream()
                        .filter(MetadataDtos.ColumnInfo::primaryKey)
                        .map(MetadataDtos.ColumnInfo::name)
                        .findFirst().orElse("ID");
                String condition = tableName(entry.getValue()) + "." + column.name()
                        + " = " + tableName(target) + "." + targetColumn;
                SemanticJoin join = joins.findByTenantIdAndFromTermIdAndToTermId(
                                tenant, sourceTerm.getId(), targetTerm.getId())
                        .orElseGet(SemanticJoin::new);
                if (join.getId() != null) {
                    continue;
                }
                join.setTenantId(tenant);
                join.setName(sourceTerm.getTerm() + " -> " + targetTerm.getTerm());
                join.setFromTermId(sourceTerm.getId());
                join.setToTermId(targetTerm.getId());
                join.setJoinCondition(condition);
                join.setRelationshipType("MANY_TO_ONE");
                join.setConfidence(75);
                join.setStatus("DRAFT");
                join.setSource("INFERRED");
                join.setProvenance("column:" + tableName(entry.getValue()) + "." + column.name()
                        + ";datasource:" + datasourceId);
                joins.save(join);
                created++;
            }
        }
        return created;
    }

    private String findTargetTable(String stem, Set<String> tables) {
        for (String candidate : List.of(stem, stem + "S", stem + "ES")) {
            if (tables.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public SemanticDtos.Answerability answerable(String question) {
        String q = question == null ? "" : question.trim();
        List<SemanticTerm> all = terms.findAllByOrderByTermAsc();
        List<SemanticTerm> matched = embeddings.rank(q, all,
                term -> term.getTerm() + " " + nullToEmpty(term.getDefinition())
                        + " " + nullToEmpty(term.getSynonyms()), 6);
        List<SemanticTermColumn> columns = new ArrayList<>();
        List<SemanticSqlAttribute> attributes = new ArrayList<>();
        List<SemanticJoin> joinRows = new ArrayList<>();
        Set<UUID> termIds = new LinkedHashSet<>();
        for (SemanticTerm term : matched) {
            termIds.add(term.getId());
            columns.addAll(termColumns.findByTermId(term.getId()));
            attributes.addAll(sqlAttributes.findByTermIdOrderByStatusAscNameAsc(term.getId()));
        }
        for (SemanticJoin join : joins.findByTenantIdOrderByConfidenceDesc(tenant())) {
            if (termIds.contains(join.getFromTermId()) || termIds.contains(join.getToTermId())) {
                joinRows.add(join);
            }
        }
        List<String> missing = new ArrayList<>();
        List<String> tokens = meaningfulTokens(q);
        int covered = 0;
        for (String token : tokens) {
            boolean hit = matched.stream().anyMatch(term -> (term.getTerm() + " "
                    + nullToEmpty(term.getDefinition()) + " " + nullToEmpty(term.getSynonyms()))
                    .toLowerCase(Locale.ROOT).contains(token));
            if (hit) {
                covered++;
            } else {
                missing.add(token);
            }
        }
        double coverage = tokens.isEmpty() ? 0 : covered / (double) tokens.size();
        boolean answerable = !matched.isEmpty() && coverage >= 0.35;
        String explanation = answerable
                ? "matched " + matched.size() + " terms; " + columns.size() + " column mappings, "
                + attributes.size() + " SQL attributes, " + joinRows.size() + " joins"
                : "ontology coverage is too low; ask a clarifying question or compile more sources";
        return new SemanticDtos.Answerability(q, answerable, round(coverage),
                matched.stream().map(this::termView).toList(),
                columns.stream().map(this::columnView).toList(),
                attributes.stream().map(this::attributeView).toList(),
                joinRows.stream().map(this::joinView).toList(),
                missing, explanation);
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.JoinView> joinPath(UUID fromTermId, UUID toTermId) {
        if (fromTermId == null || toTermId == null) {
            return List.of();
        }
        if (fromTermId.equals(toTermId)) {
            return List.of();
        }
        List<SemanticJoin> all = joins.findByTenantIdOrderByConfidenceDesc(tenant());
        Map<UUID, List<SemanticJoin>> graph = new LinkedHashMap<>();
        for (SemanticJoin join : all) {
            graph.computeIfAbsent(join.getFromTermId(), ignored -> new ArrayList<>()).add(join);
            graph.computeIfAbsent(join.getToTermId(), ignored -> new ArrayList<>()).add(join);
        }
        Queue<UUID> queue = new ArrayDeque<>();
        Map<UUID, SemanticJoin> previous = new HashMap<>();
        Set<UUID> visited = new LinkedHashSet<>();
        queue.add(fromTermId);
        visited.add(fromTermId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(toTermId)) {
                break;
            }
            for (SemanticJoin join : graph.getOrDefault(current, List.of())) {
                UUID next = join.getFromTermId().equals(current) ? join.getToTermId() : join.getFromTermId();
                if (visited.add(next)) {
                    previous.put(next, join);
                    queue.add(next);
                }
            }
        }
        if (!visited.contains(toTermId)) {
            return List.of();
        }
        List<SemanticJoin> path = new ArrayList<>();
        UUID cursor = toTermId;
        while (!cursor.equals(fromTermId)) {
            SemanticJoin join = previous.get(cursor);
            if (join == null) {
                return List.of();
            }
            path.add(0, join);
            cursor = join.getFromTermId().equals(cursor) ? join.getToTermId() : join.getFromTermId();
        }
        return path.stream().map(this::joinView).toList();
    }

    @Transactional(readOnly = true)
    public SemanticDtos.OntologyGraph graph(UUID termId) {
        List<SemanticTerm> termRows = terms.findAllByOrderByTermAsc();
        List<SemanticJoin> joinRows = joins.findByTenantIdOrderByConfidenceDesc(tenant());
        List<SemanticDtos.OntologyNode> nodes = new ArrayList<>();
        List<SemanticDtos.OntologyEdge> edges = new ArrayList<>();
        Set<UUID> visible = new LinkedHashSet<>();
        for (SemanticTerm term : termRows) {
            if (termId != null && !term.getId().equals(termId)) {
                continue;
            }
            visible.add(term.getId());
            nodes.add(new SemanticDtos.OntologyNode("term:" + term.getId(), "TERM",
                    term.getTerm(), term.getStatus(), nullToEmpty(term.getDefinition())));
            for (SemanticTermColumn column : termColumns.findByTermId(term.getId())) {
                String columnId = "column:" + column.getId();
                nodes.add(new SemanticDtos.OntologyNode(columnId, "COLUMN",
                        column.getTableName() + "." + column.getColumnName(), column.getStatus(),
                        column.getRole()));
                edges.add(new SemanticDtos.OntologyEdge("term:" + term.getId(), columnId,
                        "HAS_COLUMN", column.getRole(), column.getConfidence()));
            }
            for (SemanticSqlAttribute attribute : sqlAttributes.findByTermIdOrderByStatusAscNameAsc(term.getId())) {
                String attributeId = "attribute:" + attribute.getId();
                nodes.add(new SemanticDtos.OntologyNode(attributeId, "SQL_ATTRIBUTE",
                        attribute.getDisplayName(), attribute.getStatus(), attribute.getExpression()));
                edges.add(new SemanticDtos.OntologyEdge("term:" + term.getId(), attributeId,
                        "HAS_ATTRIBUTE", attribute.getName(), attribute.getConfidence()));
            }
        }
        for (SemanticJoin join : joinRows) {
            if (!visible.contains(join.getFromTermId()) && !visible.contains(join.getToTermId())) {
                continue;
            }
            edges.add(new SemanticDtos.OntologyEdge("term:" + join.getFromTermId(),
                    "term:" + join.getToTermId(), "JOIN", join.getJoinCondition(), join.getConfidence()));
        }
        return new SemanticDtos.OntologyGraph(nodes, edges);
    }

    @Transactional(readOnly = true)
    public SemanticDtos.OntologyGraph fullGraph() {
        return graph(null);
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.TermColumnView> columnsForTerm(UUID termId) {
        return termColumns.findByTermId(termId).stream().map(this::columnView).toList();
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.TermColumnView> columnsForTable(String table) {
        return termColumns.findByTenantIdAndTableNameIgnoreCase(tenant(), table).stream()
                .map(this::columnView).toList();
    }

    @Transactional(readOnly = true)
    public SemanticDtos.OntologyImpact impact(String table, String column) {
        List<SemanticTermColumn> mappings = termColumns.findByTenantIdAndTableNameIgnoreCase(tenant(), table);
        if (column != null && !column.isBlank()) {
            mappings = mappings.stream()
                    .filter(mapping -> mapping.getColumnName().equalsIgnoreCase(column))
                    .toList();
        }
        List<SemanticTerm> termRows = mappings.stream()
                .map(mapping -> terms.findById(mapping.getTermId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<SemanticSqlAttribute> attributes = new ArrayList<>();
        for (SemanticTerm term : termRows) {
            attributes.addAll(sqlAttributes.findByTermIdOrderByStatusAscNameAsc(term.getId()));
        }
        Set<UUID> ids = termRows.stream().map(SemanticTerm::getId).collect(java.util.stream.Collectors.toSet());
        List<SemanticJoin> relatedJoins = joins.findByTenantIdOrderByConfidenceDesc(tenant()).stream()
                .filter(join -> ids.contains(join.getFromTermId()) || ids.contains(join.getToTermId()))
                .toList();
        return new SemanticDtos.OntologyImpact(table, column,
                termRows.stream().map(this::termView).toList(),
                mappings.stream().map(this::columnView).toList(),
                attributes.stream().map(this::attributeView).toList(),
                relatedJoins.stream().map(this::joinView).toList());
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.SqlAttributeView> sqlAttributes(String query) {
        return sqlAttributes.search(tenant(), query == null || query.isBlank() ? null : query.trim())
                .stream().map(this::attributeView).toList();
    }

    @Transactional
    public SemanticDtos.TermColumnView certifyColumn(UUID id, SemanticDtos.CertifyRequest request) {
        SemanticTermColumn column = termColumns.findById(id)
                .orElseThrow(() -> ApiException.notFound("term column 不存在: " + id));
        column.setStatus(status(request == null ? null : request.status()));
        column.setConfidence(column.getStatus().equals("CERTIFIED") ? 95 : column.getConfidence());
        if (request != null && request.note() != null) {
            column.setProvenance(column.getProvenance() + ";certify:" + request.note());
        }
        return columnView(termColumns.save(column));
    }

    @Transactional
    public SemanticDtos.SqlAttributeView certifyAttribute(UUID id, SemanticDtos.CertifyRequest request) {
        SemanticSqlAttribute attribute = sqlAttributes.findById(id)
                .orElseThrow(() -> ApiException.notFound("SQL attribute 不存在: " + id));
        attribute.setStatus(status(request == null ? null : request.status()));
        attribute.setOwner(request == null ? attribute.getOwner() : request.owner());
        if (request != null && request.note() != null) {
            attribute.setProvenance(attribute.getProvenance() + ";certify:" + request.note());
        }
        return attributeView(sqlAttributes.save(attribute));
    }

    @Transactional
    public SemanticDtos.JoinView certifyJoin(UUID id, SemanticDtos.CertifyRequest request) {
        SemanticJoin join = joins.findById(id)
                .orElseThrow(() -> ApiException.notFound("semantic join 不存在: " + id));
        join.setStatus(status(request == null ? null : request.status()));
        join.setConfidence(join.getStatus().equals("CERTIFIED") ? 95 : join.getConfidence());
        return joinView(joins.save(join));
    }

    @Transactional
    public SemanticDtos.JoinView createJoin(SemanticDtos.JoinRequest request) {
        terms.findById(request.fromTermId())
                .orElseThrow(() -> ApiException.badRequest("from term 不存在"));
        terms.findById(request.toTermId())
                .orElseThrow(() -> ApiException.badRequest("to term 不存在"));
        SemanticJoin join = joins.findByTenantIdAndFromTermIdAndToTermId(
                tenant(), request.fromTermId(), request.toTermId()).orElseGet(SemanticJoin::new);
        join.setTenantId(tenant());
        join.setName(request.name());
        join.setFromTermId(request.fromTermId());
        join.setToTermId(request.toTermId());
        join.setJoinCondition(request.joinCondition());
        join.setRelationshipType(request.relationshipType() == null ? "MANY_TO_ONE"
                : request.relationshipType().toUpperCase(Locale.ROOT));
        join.setConfidence(request.confidence() == null ? 70 : Math.max(0, Math.min(100, request.confidence())));
        join.setStatus(request.status() == null ? "DRAFT" : request.status().toUpperCase(Locale.ROOT));
        join.setSource(request.source() == null ? "MANUAL" : request.source().toUpperCase(Locale.ROOT));
        join.setProvenance("manual:" + AuthContext.currentOrSystem().userId());
        return joinView(joins.save(join));
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.CompileRunView> compileRuns() {
        return compileRuns.findTop20ByTenantIdOrderByStartedAtDesc(tenant()).stream()
                .map(this::compileView).toList();
    }

    @Transactional(readOnly = true)
    public SemanticDtos.CompileRunView lastCompile() {
        return compileRuns.findTop20ByTenantIdOrderByStartedAtDesc(tenant()).stream()
                .findFirst().map(this::compileView).orElse(null);
    }

    private SemanticDtos.TermView termView(SemanticTerm term) {
        return new SemanticDtos.TermView(term.getId(), term.getTerm(), term.getDefinition(),
                term.getSynonyms(), term.getRelatedEntityId(), null, term.getRelatedMetricId(), null,
                term.getOwner(), term.getTags(), term.getStatus(), term.getCreatedAt(), term.getUpdatedAt());
    }

    private SemanticDtos.TermColumnView columnView(SemanticTermColumn column) {
        return new SemanticDtos.TermColumnView(column.getId(), column.getTermId(),
                terms.findById(column.getTermId()).map(SemanticTerm::getTerm).orElse(null),
                column.getDatasourceId(), column.getSchemaName(), column.getTableName(),
                column.getColumnName(), column.getRole(), column.getConfidence(), column.getStatus(),
                column.getProvenance(), column.getUpdatedAt());
    }

    private SemanticDtos.SqlAttributeView attributeView(SemanticSqlAttribute attribute) {
        return new SemanticDtos.SqlAttributeView(attribute.getId(), attribute.getTermId(),
                terms.findById(attribute.getTermId()).map(SemanticTerm::getTerm).orElse(null),
                attribute.getName(), attribute.getDisplayName(), attribute.getExpression(),
                attribute.getBusinessDefinition(), attribute.getGrain(), attribute.getSynonyms(),
                attribute.getOwner(), attribute.getTags(), attribute.getStatus(), attribute.getConfidence(),
                attribute.getSource(), attribute.getUsageCount(), attribute.getLastUsedAt(),
                attribute.getProvenance(), attribute.getUpdatedAt());
    }

    private SemanticDtos.JoinView joinView(SemanticJoin join) {
        return new SemanticDtos.JoinView(join.getId(), join.getName(), join.getFromTermId(),
                terms.findById(join.getFromTermId()).map(SemanticTerm::getTerm).orElse(null),
                join.getToTermId(),
                terms.findById(join.getToTermId()).map(SemanticTerm::getTerm).orElse(null),
                join.getJoinCondition(), join.getRelationshipType(), join.getConfidence(),
                join.getStatus(), join.getSource(), join.getProvenance(), join.getUpdatedAt());
    }

    private SemanticDtos.CompileRunView compileView(SemanticCompileRun run) {
        return new SemanticDtos.CompileRunView(run.getId(), run.getDatasourceId(), run.getSchemaName(),
                run.getStatus(), run.getScannedTables(), run.getScannedColumns(), run.getCreatedTerms(),
                run.getMappedColumns(), run.getCreatedJoins(), run.getCreatedAttributes(),
                run.getMessage(), run.getStartedAt(), run.getFinishedAt());
    }

    private static String tableName(MetadataDtos.TableDetail detail) {
        return detail == null ? "unknown" : (detail.schema() == null ? detail.name()
                : detail.schema() + "." + detail.name());
    }

    private static List<String> meaningfulTokens(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(question.toLowerCase(Locale.ROOT)
                        .split("[^a-z0-9\\u4e00-\\u9fa5_]+"))
                .map(String::trim)
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token))
                .distinct()
                .toList();
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String status(String value) {
        return value == null || value.isBlank() ? "CERTIFIED" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String tenant() {
        return AuthContext.currentOrSystem().tenantId();
    }
}
