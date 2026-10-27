package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record KnowledgeView(
            UUID id,
            String title,
            String problem,
            String sqlText,
            String optimizedSql,
            String dialect,
            String tags,
            String planSummary,
            String rootCause,
            String optimizationSteps,
            Long beforeDurationMs,
            Long afterDurationMs,
            String author,
            UUID sourceQueryId,
            int usageCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record KnowledgePage(List<KnowledgeView> items, long total, int page, int size, int totalPages) {
    }

    public record KnowledgeRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 8000) String problem,
            @NotBlank @Size(max = 100000) String sqlText,
            @Size(max = 100000) String optimizedSql,
            @Size(max = 32) String dialect,
            @Size(max = 500) String tags,
            @Size(max = 8000) String planSummary,
            @Size(max = 8000) String rootCause,
            @Size(max = 8000) String optimizationSteps,
            Long beforeDurationMs,
            Long afterDurationMs,
            @Size(max = 120) String author,
            UUID sourceQueryId
    ) {
    }
}
