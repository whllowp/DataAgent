package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class VerifiedQueryDtos {

    private VerifiedQueryDtos() {
    }

    public record View(
            UUID id,
            String question,
            String sqlText,
            UUID datasourceId,
            UUID metricId,
            UUID entityId,
            String tags,
            String status,
            String owner,
            String notes,
            String expectedColumns,
            int useCount,
            Instant lastValidatedAt,
            String lastValidationStatus,
            Long lastDurationMs,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record Request(
            @NotBlank @Size(max = 1000) String question,
            @NotBlank @Size(max = 100000) String sqlText,
            UUID datasourceId,
            UUID metricId,
            UUID entityId,
            @Size(max = 500) String tags,
            @Size(max = 20) String status,
            @Size(max = 120) String owner,
            @Size(max = 8000) String notes,
            @Size(max = 2000) String expectedColumns
    ) {
    }

    public record ValidateResult(
            View query,
            boolean success,
            int rowCount,
            long durationMs,
            String message
    ) {
    }
}
