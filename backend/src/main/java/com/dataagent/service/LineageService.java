package com.dataagent.service;

import com.dataagent.domain.EtlTask;
import com.dataagent.domain.EtlWorkflow;
import com.dataagent.domain.LineageEdge;
import com.dataagent.domain.LineageFieldEdge;
import com.dataagent.domain.SemanticEntity;
import com.dataagent.domain.SqlAsset;
import com.dataagent.domain.VerifiedQuery;
import com.dataagent.repository.EtlTaskRepository;
import com.dataagent.repository.EtlWorkflowRepository;
import com.dataagent.repository.LineageEdgeRepository;
import com.dataagent.repository.LineageFieldEdgeRepository;
import com.dataagent.repository.SemanticEntityRepository;
import com.dataagent.repository.SqlAssetRepository;
import com.dataagent.repository.VerifiedQueryRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.dto.LineageDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LineageService {

    private static final Pattern CTE = Pattern.compile(
            "(?i)(?:\\bWITH\\b|,)\\s*([a-z_][\\w$]*)\\s+AS\\s*\\(");
    private static final Pattern TABLE = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|UPDATE|INTO|DELETE\\s+FROM|MERGE\\s+INTO)\\s+([a-z_][\\w$]*(?:\\.[a-z_][\\w$]*)?)");
    private static final Pattern TABLE_ALIAS = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN)\\s+([a-z_][\\w$]*(?:\\.[a-z_][\\w$]*)?)(?:\\s+(?:AS\\s+)?([a-z_][\\w$]*))?");
    private static final Pattern QUALIFIED_FIELD = Pattern.compile(
            "(?i)\\b([a-z_][\\w$]*)\\.([a-z_][\\w$]*)\\b");
    private static final Pattern TARGET = Pattern.compile(
            "(?i)\\b(?:INSERT\\s+(?:OVERWRITE\\s+)?(?:INTO\\s+)?(?:TABLE\\s+)?|MERGE\\s+INTO\\s+|CREATE\\s+TABLE\\s+)([a-z_][\\w$]*(?:\\.[a-z_][\\w$]*)?)");
    private static final Set<String> ALIAS_STOP = Set.of(
            "WHERE", "ON", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS",
            "GROUP", "ORDER", "LIMIT", "OFFSET", "UNION", "AS", "USING", "SET", "VALUES",
            "SELECT", "WITH", "HAVING", "WINDOW", "FETCH");

    private final SqlAssetRepository assets;
    private final VerifiedQueryRepository verifiedQueries;
    private final SemanticEntityRepository entities;
    private final PiiService pii;
    private final EtlWorkflowRepository etlWorkflows;
    private final EtlTaskRepository etlTasks;
    private final LineageEdgeRepository lineageEdges;
    private final LineageFieldEdgeRepository lineageFieldEdges;

    public LineageService(SqlAssetRepository assets, VerifiedQueryRepository verifiedQueries,
                          SemanticEntityRepository entities, PiiService pii,
                          EtlWorkflowRepository etlWorkflows, EtlTaskRepository etlTasks,
                          LineageEdgeRepository lineageEdges,
                          LineageFieldEdgeRepository lineageFieldEdges) {
        this.assets = assets;
        this.verifiedQueries = verifiedQueries;
        this.entities = entities;
        this.pii = pii;
        this.etlWorkflows = etlWorkflows;
        this.etlTasks = etlTasks;
        this.lineageEdges = lineageEdges;
        this.lineageFieldEdges = lineageFieldEdges;
    }

    @Transactional(readOnly = true)
    public List<LineageDtos.TableSummary> tables() {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (SqlAsset asset : assets.findAll()) {
            for (String table : extractTables(asset.getSqlText())) {
                counts.computeIfAbsent(table, ignored -> new int[4])[0]++;
            }
        }
        for (VerifiedQuery query : verifiedQueries.findAll()) {
            for (String table : extractTables(query.getSqlText())) {
                counts.computeIfAbsent(table, ignored -> new int[4])[1]++;
            }
        }
        for (SemanticEntity entity : entities.findAll()) {
            for (String table : splitTables(entity.getPhysicalTables())) {
                counts.computeIfAbsent(table, ignored -> new int[4])[2]++;
            }
        }
        for (EtlTask task : tenantEtlTasks()) {
            Set<String> touched = new LinkedHashSet<>(extractTables(task.getSqlText()));
            touched.addAll(splitTables(task.getSourceTables()));
            if (task.getTargetTable() != null) {
                touched.add(canonical(task.getTargetTable()));
            }
            for (String table : touched) {
                counts.computeIfAbsent(table, ignored -> new int[4])[3]++;
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new LineageDtos.TableSummary(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2], entry.getValue()[3]))
                .sorted(Comparator.comparing(LineageDtos.TableSummary::table))
                .toList();
    }

    @Transactional(readOnly = true)
    public LineageDtos.Impact impact(String table) {
        String normalized = table == null ? "" : canonical(table);
        List<LineageDtos.AssetRef> assetRefs = assets.findAll().stream()
                .filter(asset -> references(asset.getSqlText(), normalized))
                .sorted(Comparator.comparing(SqlAsset::getLastSeen,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(asset -> new LineageDtos.AssetRef(asset.getId(), asset.getBusinessProblem(),
                        preview(asset.getSqlText()), asset.getDatasourceId(),
                        asset.getExecutionCount(), asset.getLastSeen()))
                .toList();
        List<LineageDtos.VerifiedRef> verifiedRefs = verifiedQueries.findAll().stream()
                .filter(query -> references(query.getSqlText(), normalized))
                .map(query -> new LineageDtos.VerifiedRef(query.getId(), query.getQuestion(),
                        query.getStatus()))
                .toList();
        List<LineageDtos.EntityRef> entityRefs = entities.findAll().stream()
                .filter(entity -> splitTables(entity.getPhysicalTables()).contains(normalized))
                .map(entity -> new LineageDtos.EntityRef(entity.getId(), entity.getName(),
                        entity.getDisplayName(), entity.getPhysicalTables()))
                .toList();
        List<EtlWorkflow> workflowList = etlWorkflows.findByTenantIdOrderByNameAsc(
                AuthContext.currentOrSystem().tenantId());
        Map<java.util.UUID, EtlWorkflow> workflowById = workflowList.stream()
                .collect(java.util.stream.Collectors.toMap(EtlWorkflow::getId, item -> item));
        List<LineageDtos.EtlRef> etlRefs = tenantEtlTasks().stream()
                .filter(task -> references(task.getSqlText(), normalized)
                        || splitTables(task.getSourceTables()).contains(normalized)
                        || normalized.equals(canonical(task.getTargetTable())))
                .map(task -> {
                    EtlWorkflow workflow = workflowById.get(task.getWorkflowId());
                    return new LineageDtos.EtlRef(task.getId(), task.getName(), task.getTaskType(),
                            task.getWorkflowId(), workflow == null ? null : workflow.getName(),
                            workflow == null ? null : workflow.getScheduleCron(),
                            task.getSourceTables(), task.getTargetTable());
                })
                .toList();
        String tenant = AuthContext.currentOrSystem().tenantId();
        List<LineageDtos.OpenLineageRef> openLineageRefs = lineageEdges
                .findByTenantIdAndSourceTable(tenant, normalized).stream()
                .filter(edge -> "OPENLINEAGE".equals(edge.getKind()))
                .map(edge -> new LineageDtos.OpenLineageRef(edge.getJobName(), edge.getRunId(),
                        edge.getSourceTable(), edge.getTargetTable()))
                .toList();
        return new LineageDtos.Impact(normalized, assetRefs, verifiedRefs, entityRefs, etlRefs,
                openLineageRefs);
    }

    private List<EtlTask> tenantEtlTasks() {
        List<EtlWorkflow> workflowList = etlWorkflows.findByTenantIdOrderByNameAsc(
                AuthContext.currentOrSystem().tenantId());
        if (workflowList.isEmpty()) {
            return List.of();
        }
        return etlTasks.findByWorkflowIdIn(workflowList.stream().map(EtlWorkflow::getId).toList());
    }

    public Set<String> extractTables(String sql) {
        Set<String> result = new LinkedHashSet<>();
        if (sql == null || sql.isBlank()) {
            return result;
        }
        String cleaned = strip(sql);
        Set<String> ctes = new LinkedHashSet<>();
        Matcher cteMatcher = CTE.matcher(cleaned);
        while (cteMatcher.find()) {
            ctes.add(cteMatcher.group(1).toUpperCase(Locale.ROOT));
        }
        Matcher matcher = TABLE.matcher(cleaned);
        while (matcher.find()) {
            String table = canonical(matcher.group(1));
            if (!ctes.contains(table) && !table.startsWith("SELECT")) {
                result.add(table);
            }
        }
        return result;
    }

    public String extractTargetTable(String sql) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        Matcher matcher = TARGET.matcher(strip(sql));
        return matcher.find() ? canonical(matcher.group(1)) : null;
    }

    private static String canonical(String table) {
        String upper = table.toUpperCase(Locale.ROOT);
        int dot = upper.lastIndexOf('.');
        return dot >= 0 ? upper.substring(dot + 1) : upper;
    }

    public Set<String> extractFields(String sql) {
        Set<String> result = new LinkedHashSet<>();
        if (sql == null || sql.isBlank()) {
            return result;
        }
        String cleaned = strip(sql);
        Map<String, String> aliases = aliasMap(cleaned);
        Matcher matcher = QUALIFIED_FIELD.matcher(cleaned);
        while (matcher.find()) {
            String table = aliases.get(matcher.group(1).toLowerCase(Locale.ROOT));
            if (table != null) {
                result.add(table + "." + matcher.group(2).toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LineageDtos.FieldSummary> fields() {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (SqlAsset asset : assets.findAll()) {
            for (String field : extractFields(asset.getSqlText())) {
                counts.computeIfAbsent(field, ignored -> new int[2])[0]++;
            }
        }
        for (VerifiedQuery query : verifiedQueries.findAll()) {
            for (String field : extractFields(query.getSqlText())) {
                counts.computeIfAbsent(field, ignored -> new int[2])[1]++;
            }
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\.", 2);
                    return new LineageDtos.FieldSummary(parts[0], parts[1],
                            pii.classify(parts[1], parts[1]), entry.getValue()[0], entry.getValue()[1]);
                })
                .sorted(Comparator.comparing(LineageDtos.FieldSummary::table)
                        .thenComparing(LineageDtos.FieldSummary::column))
                .toList();
    }

    @Transactional(readOnly = true)
    public LineageDtos.FieldImpact fieldImpact(String table, String column) {
        String normalizedTable = canonical(table);
        String normalizedColumn = column == null ? "" : column.trim().toUpperCase(Locale.ROOT);
        String field = normalizedTable + "." + normalizedColumn;
        List<LineageDtos.AssetRef> assetRefs = assets.findAll().stream()
                .filter(asset -> extractFields(asset.getSqlText()).contains(field))
                .map(asset -> new LineageDtos.AssetRef(asset.getId(), asset.getBusinessProblem(),
                        preview(asset.getSqlText()), asset.getDatasourceId(),
                        asset.getExecutionCount(), asset.getLastSeen()))
                .toList();
        List<LineageDtos.VerifiedRef> verifiedRefs = verifiedQueries.findAll().stream()
                .filter(query -> extractFields(query.getSqlText()).contains(field))
                .map(query -> new LineageDtos.VerifiedRef(query.getId(), query.getQuestion(),
                        query.getStatus()))
                .toList();
        List<LineageDtos.FieldEdgeRef> fieldEdges = lineageFieldEdges
                .findByTenantIdAndSourceTableAndSourceColumn(AuthContext.currentOrSystem().tenantId(),
                        normalizedTable, normalizedColumn).stream()
                .map(edge -> new LineageDtos.FieldEdgeRef(edge.getSourceTable(), edge.getSourceColumn(),
                        edge.getTargetTable(), edge.getTargetColumn(), edge.getExpression(),
                        edge.getKind(), edge.getJobName()))
                .toList();
        return new LineageDtos.FieldImpact(normalizedTable, normalizedColumn,
                pii.classify(normalizedColumn, normalizedColumn), assetRefs, verifiedRefs, fieldEdges);
    }

    public Map<String, String> tableAliases(String sql) {
        return aliasMap(sql);
    }

    private Map<String, String> aliasMap(String sql) {
        Map<String, String> aliases = new LinkedHashMap<>();
        Set<String> cteNames = new LinkedHashSet<>();
        Matcher cteMatcher = CTE.matcher(sql);
        while (cteMatcher.find()) {
            cteNames.add(cteMatcher.group(1).toUpperCase(Locale.ROOT));
        }
        Matcher matcher = TABLE_ALIAS.matcher(sql);
        while (matcher.find()) {
            String table = canonical(matcher.group(1));
            if (cteNames.contains(table)) {
                continue;
            }
            aliases.put(table.toLowerCase(Locale.ROOT), table);
            String alias = matcher.group(2);
            if (alias != null && !alias.isBlank()
                    && !ALIAS_STOP.contains(alias.toUpperCase(Locale.ROOT))) {
                aliases.put(alias.toLowerCase(Locale.ROOT), table);
            }
        }
        return aliases;
    }

    private boolean references(String sql, String table) {
        return extractTables(sql).contains(table);
    }

    private static List<String> splitTables(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String piece : value.split("[,，;；\\s]+")) {
            if (!piece.isBlank()) {
                result.add(canonical(piece.trim()));
            }
        }
        return result;
    }

    private static String strip(String sql) {
        return sql.replaceAll("--[^\\n]*", " ")
                .replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("'(?:''|[^'])*'", "''");
    }

    private static String preview(String sql) {
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 240 ? oneLine : oneLine.substring(0, 240) + "…";
    }
}
