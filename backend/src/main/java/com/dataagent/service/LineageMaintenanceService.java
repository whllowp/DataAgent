package com.dataagent.service;

import com.dataagent.domain.EtlTask;
import com.dataagent.domain.EtlWorkflow;
import com.dataagent.domain.LineageEdge;
import com.dataagent.domain.LineageFieldEdge;
import com.dataagent.repository.EtlTaskRepository;
import com.dataagent.repository.EtlWorkflowRepository;
import com.dataagent.repository.LineageEdgeRepository;
import com.dataagent.repository.LineageFieldEdgeRepository;
import com.dataagent.web.dto.EtlDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LineageMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(LineageMaintenanceService.class);

    private final EtlWorkflowRepository workflows;
    private final EtlTaskRepository tasks;
    private final LineageEdgeRepository edges;
    private final LineageFieldEdgeRepository fieldEdges;
    private final LineageService lineage;
    private final FieldMappingParser fieldParser;
    private final TargetSchemaResolver targetSchema;

    public LineageMaintenanceService(EtlWorkflowRepository workflows, EtlTaskRepository tasks,
                                     LineageEdgeRepository edges,
                                     LineageFieldEdgeRepository fieldEdges,
                                     LineageService lineage, FieldMappingParser fieldParser,
                                     TargetSchemaResolver targetSchema) {
        this.workflows = workflows;
        this.tasks = tasks;
        this.edges = edges;
        this.fieldEdges = fieldEdges;
        this.lineage = lineage;
        this.fieldParser = fieldParser;
        this.targetSchema = targetSchema;
    }

    @Transactional
    public int rebuildTenant(String tenantId) {
        edges.deleteByTenantIdAndKind(tenantId, "ETL_TASK");
        fieldEdges.deleteByTenantIdAndKind(tenantId, "ETL_TASK");
        List<EtlWorkflow> workflowList = workflows.findByTenantIdOrderByNameAsc(tenantId);
        if (workflowList.isEmpty()) {
            return 0;
        }
        Map<UUID, EtlWorkflow> workflowById = workflowList.stream()
                .collect(java.util.stream.Collectors.toMap(EtlWorkflow::getId, item -> item));
        List<EtlTask> taskList = tasks.findByWorkflowIdIn(workflowList.stream()
                .map(EtlWorkflow::getId).toList());
        int count = 0;
        for (EtlTask task : taskList) {
            EtlWorkflow workflow = workflowById.get(task.getWorkflowId());
            if (workflow == null || !workflow.isEnabled() || !task.isEnabled()) {
                continue;
            }
            String target = task.getTargetTable();
            if (target == null || target.isBlank()) {
                continue;
            }
            target = canonical(target);
            List<String> sourceTables = split(task.getSourceTables());
            for (String source : sourceTables) {
                LineageEdge edge = new LineageEdge();
                edge.setTenantId(tenantId);
                edge.setSourceTable(source);
                edge.setTargetTable(target);
                edge.setKind("ETL_TASK");
                edge.setJobName(workflow.getName());
                edge.setWorkflowId(workflow.getId());
                edge.setTaskId(task.getId());
                edges.save(edge);
                count++;
            }
            List<EtlDtos.FieldMapping> mappings = fieldParser.parse(task.getSqlText(), lineage,
                    targetSchema.columns(task.getDatasourceId(), target));
            if (mappings.isEmpty() && sourceTables.size() == 1) {
                mappings = List.of(new EtlDtos.FieldMapping(sourceTables.get(0), "*", target, "*",
                        "task output", true));
            }
            for (EtlDtos.FieldMapping mapping : mappings) {
                String sourceTable = mapping.sourceTable() == null
                        ? (sourceTables.isEmpty() ? null : sourceTables.get(0)) : canonical(mapping.sourceTable());
                if (sourceTable == null) {
                    continue;
                }
                LineageFieldEdge field = new LineageFieldEdge();
                field.setTenantId(tenantId);
                field.setSourceTable(sourceTable);
                field.setSourceColumn(mapping.sourceColumn());
                field.setTargetTable(target);
                field.setTargetColumn(mapping.targetColumn() == null ? "*" : mapping.targetColumn());
                field.setExpression(mapping.expression());
                field.setKind("ETL_TASK");
                field.setJobName(workflow.getName());
                field.setWorkflowId(workflow.getId());
                field.setTaskId(task.getId());
                field.setWildcard(mapping.wildcard());
                fieldEdges.save(field);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public void rebuildAll() {
        workflows.findAll().stream().map(EtlWorkflow::getTenantId).distinct()
                .forEach(this::rebuildTenant);
    }

    @Scheduled(cron = "${dataagent.lineage.rebuild-cron:0 10 * * * *}")
    public void scheduledRebuild() {
        try {
            rebuildAll();
        } catch (Exception error) {
            log.warn("Scheduled lineage rebuild failed: {}", error.getMessage());
        }
    }

    public List<EtlDtos.FieldLineage> fieldLineage(String table, String column) {
        String tenant = com.dataagent.security.AuthContext.currentOrSystem().tenantId();
        List<LineageFieldEdge> items = table == null || table.isBlank()
                ? fieldEdges.findByTenantId(tenant)
                : fieldEdges.findByTenantIdAndSourceTableAndSourceColumn(tenant,
                canonical(table), column == null ? null : column.toUpperCase(Locale.ROOT));
        return items.stream().map(this::fieldView).toList();
    }

    public List<EtlDtos.FieldLineage> taskFields(UUID taskId) {
        String tenant = com.dataagent.security.AuthContext.currentOrSystem().tenantId();
        return fieldEdges.findByTenantIdAndTaskId(tenant, taskId).stream().map(this::fieldView).toList();
    }

    private EtlDtos.FieldLineage fieldView(LineageFieldEdge edge) {
        String workflowName = edge.getWorkflowId() == null ? null
                : workflows.findById(edge.getWorkflowId()).map(EtlWorkflow::getName).orElse(null);
        String taskName = edge.getTaskId() == null ? edge.getJobName()
                : tasks.findById(edge.getTaskId()).map(EtlTask::getName).orElse(edge.getJobName());
        return new EtlDtos.FieldLineage(edge.getWorkflowId(), workflowName, edge.getTaskId(), taskName,
                edge.getSourceTable(), edge.getSourceColumn(), edge.getTargetTable(),
                edge.getTargetColumn(), edge.getExpression(), edge.getJobName(), edge.isWildcard());
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String piece : value.split("[,;；\\s]+")) {
            if (!piece.isBlank()) {
                result.add(canonical(piece.trim()));
            }
        }
        return result;
    }

    private static String canonical(String value) {
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        int dot = upper.lastIndexOf('.');
        return dot >= 0 ? upper.substring(dot + 1) : upper;
    }
}
