package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BenchmarkDtos {

    private BenchmarkDtos() {
    }

    public record View(
            UUID id,
            String name,
            String domain,
            String description,
            String sqlText,
            UUID datasourceId,
            Long baselineMs,
            Long thresholdMs,
            String tags,
            String status,
            Instant lastRunAt,
            Long lastDurationMs,
            String lastStatus,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record Request(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 120) String domain,
            @Size(max = 8000) String description,
            @NotBlank @Size(max = 100000) String sqlText,
            UUID datasourceId,
            Long baselineMs,
            Long thresholdMs,
            @Size(max = 500) String tags,
            @Size(max = 20) String status
    ) {
    }

    public record RunResult(View benchmark, SqlDtos.QueryResult query) {
    }

    public record RunAllResult(int attempted, int succeeded, int failed) {
    }

    public record ListResult(List<View> items) {
    }
}
