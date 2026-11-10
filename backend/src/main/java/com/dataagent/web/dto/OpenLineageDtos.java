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

    public record IngestResult(EventView event, int tableEdges, int fieldEdges) {
    }

    public record EmitResult(String jobName, String runId, int events) {
    }
}
