package com.dataagent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EtlDtos {

    private EtlDtos() {
    }

    public record WorkflowView(
            UUID id,
            String source,
            String externalId,
            String project,
            String name,
            String description,
            String scheduleCron,
            String timezone,
            String releaseState,
            boolean enabled,
            String owner,
            Instant lastSyncedAt,
            int taskCount,
            Instant nextRun,
            int upstreamWorkflowCount,
            int downstreamWorkflowCount
    ) {
    }

    public record TaskView(
            UUID id,
            UUID workflowId,
            String externalId,
            String name,
            String taskType,
            int orderIndex,
            String sqlText,
            List<String> sourceTables,
            String targetTable,
            UUID datasourceId,
            String configJson,
            boolean enabled,
            List<UUID> upstreamTaskIds,
            List<UUID> downstreamTaskIds
    ) {
    }

    public record TaskDependencyView(
            UUID id,
            UUID workflowId,
            UUID upstreamTaskId,
            UUID downstreamTaskId,
            String conditionType,
            long lagSeconds
    ) {
    }

    public record FieldMapping(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String expression,
            boolean wildcard
    ) {
    }

    public record FieldLineage(
            UUID workflowId,
            String workflowName,
            UUID taskId,
            String taskName,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String expression,
            String jobName,
            boolean wildcard
    ) {
    }

    public record WorkflowDependencyView(
            UUID id,
            UUID upstreamWorkflowId,
            String upstreamWorkflowName,
            UUID downstreamWorkflowId,
            String downstreamWorkflowName,
            String conditionType,
            long lagSeconds
    ) {
    }

    public record GraphNode(UUID id, String label, String type, String status) {
    }

    public record GraphEdge(UUID from, UUID to, String conditionType, long lagSeconds) {
    }

    public record WorkflowGraph(
            UUID workflowId,
            String workflowName,
            String scheduleCron,
            String timezone,
            Instant nextRun,
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            List<UUID> topologicalOrder,
            List<String> cycles,
            List<UUID> orphanTasks,
            List<String> sourceTables,
            List<String> targetTables
    ) {
    }

    public record LineageNode(String table, int depth) {
    }

    public record LineageEdge(
            String fromTable,
            String toTable,
            UUID taskId,
            String taskName,
            UUID workflowId,
            String workflowName,
            String scheduleCron,
            int depth
    ) {
    }

    public record TableLineage(
            String table,
            String direction,
            int depth,
            List<LineageNode> nodes,
            List<LineageEdge> edges,
            List<WorkflowView> workflows
    ) {
    }

    public record WorkflowDependencyChain(
            UUID workflowId,
            String workflowName,
            int depth,
            List<UUID> path
    ) {
    }

    public record ScheduleAnalysis(
            String scheduleCron,
            String timezone,
            Instant nextRun,
            int taskCount,
            int criticalPathLength,
            List<UUID> criticalPath,
            List<UUID> topologicalOrder
    ) {
    }

    public record Analysis(
            UUID workflowId,
            String workflowName,
            ScheduleAnalysis schedule,
            List<String> sourceTables,
            List<String> targetTables,
            List<UUID> orphanTasks,
            List<String> cycles,
            List<WorkflowDependencyChain> upstreamWorkflows,
            List<WorkflowDependencyChain> downstreamWorkflows,
            List<WorkflowView> relatedWorkflows
    ) {
    }

    public record Overview(
            long workflows,
            long tasks,
            long scheduled,
            long unscheduled,
            long dependencyCycles,
            long orphanTasks,
            List<WorkflowView> recent
    ) {
    }

    public record ImportResult(
            int workflows,
            int tasks,
            int taskDependencies,
            int workflowDependencies,
            List<String> warnings
    ) {
    }
}
