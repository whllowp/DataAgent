package com.dataagent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LineageDtos {

    private LineageDtos() {
    }

    public record TableSummary(
            String table,
            int assetCount,
            int verifiedQueryCount,
            int semanticEntityCount,
            int etlTaskCount
    ) {
    }

    public record AssetRef(
            UUID id,
            String businessProblem,
            String sqlPreview,
            UUID datasourceId,
            int executionCount,
            Instant lastSeen
    ) {
    }

    public record VerifiedRef(
            UUID id,
            String question,
            String status
    ) {
    }

    public record EntityRef(
            UUID id,
            String name,
            String displayName,
            String physicalTables
    ) {
    }

    public record EtlRef(
            UUID taskId,
            String taskName,
            String taskType,
            UUID workflowId,
            String workflowName,
            String scheduleCron,
            String sourceTables,
            String targetTable
    ) {
    }

    public record OpenLineageRef(
            String jobName,
            String runId,
            String sourceTable,
            String targetTable
    ) {
    }

    public record FieldEdgeRef(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String expression,
            String kind,
            String jobName
    ) {
    }

    public record Impact(
            String table,
            List<AssetRef> assets,
            List<VerifiedRef> verifiedQueries,
            List<EntityRef> semanticEntities,
            List<EtlRef> etlTasks,
            List<OpenLineageRef> openLineageJobs
    ) {
    }

    public record FieldImpact(
            String table,
            String column,
            String piiType,
            List<AssetRef> assets,
            List<VerifiedRef> verifiedQueries,
            List<FieldEdgeRef> fieldEdges
    ) {
    }

    public record FieldSummary(
            String table,
            String column,
            String piiType,
            int assetCount,
            int verifiedQueryCount
    ) {
    }
}
