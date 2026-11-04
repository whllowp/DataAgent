package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record Request(
            @Size(max = 200) String sessionId,
            @NotBlank @Size(max = 2000) String question,
            @Size(max = 100000) String originalSql,
            @Size(max = 100000) String correctedSql,
            @NotBlank @Size(max = 8000) String correction,
            Integer rating,
            @Size(max = 8000) String notes,
            UUID datasourceId,
            UUID metricId,
            UUID entityId,
            @Size(max = 120) String domain,
            @Size(max = 120) String reviewer
    ) {
    }

    public record View(
            UUID id,
            String sessionId,
            String question,
            String originalSql,
            String correctedSql,
            String correction,
            Integer rating,
            String notes,
            UUID datasourceId,
            UUID metricId,
            UUID entityId,
            UUID memoryId,
            UUID verifiedQueryId,
            Instant createdAt
    ) {
    }

    public record Result(
            View feedback,
            MemoryDtos.MemoryView memory,
            VerifiedQueryDtos.View verifiedQuery,
            List<MemoryDtos.MemoryView> conflicts
    ) {
    }
}
