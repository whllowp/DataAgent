package com.dataagent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OpenLineageDtos {

    private OpenLineageDtos() {
    }

    public record EventView(
            UUID id,
            Instant eventTime,
            String eventType,
            String jobNamespace,
            String jobName,
            String runId,
            String producer,
            String inputsJson,
            String outputsJson,
            String columnLineageJson,
            Instant createdAt
    ) {
    }

    public record JobView(
            String namespace,
            String name,
            long events,
            Instant lastEventAt
    ) {
    }

    public record RunTableEdge(String sourceTable, String targetTable) {
    }

    public record RunFieldEdge(String sourceTable, String sourceColumn, String targetTable,
                               String targetColumn, String expression) {
    }

    public record RunView(
            String runId,
            String eventType,
            Instant eventTime,
            String inputsJson,
            String outputsJson,
            String columnLineageJson,
            List<RunTableEdge> tableEdges,
            List<RunFieldEdge> fieldEdges
    ) {
    }

    public record JobDetail(
            String namespace,
            String name,
            Instant createdAt,
            Instant updatedAt,
            List<RunView> runs
    ) {
    }

    public record NamespaceView(String namespace, long jobs, long events) {
    }

    public record IngestResult(EventView event, int tableEdges, int fieldEdges) {
    }

    public record EmitResult(String jobName, String runId, int events) {
    }
}
