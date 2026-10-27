package com.dataagent.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DatasourceDtos {

    private DatasourceDtos() {
    }

    public record DatasourceView(
            UUID id,
            String name,
            String dialect,
            String jdbcUrl,
            String username,
            boolean readOnly,
            int maxRows,
            int queryTimeoutSeconds,
            String description,
            boolean passwordSet,
            Instant createdAt
    ) {
    }

    public record DatasourceRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 32) String dialect,
            @NotBlank @Size(max = 1000) String jdbcUrl,
            @Size(max = 200) String username,
            @Size(max = 500) String password,
            Boolean readOnly,
            @Min(1) @Max(5000) Integer maxRows,
            @Min(1) @Max(60) Integer queryTimeoutSeconds,
            @Size(max = 1000) String description
    ) {
    }

    public record TestResult(boolean ok, long latencyMs, String message, String databaseProduct, String databaseVersion) {
    }

    public record DatasourceList(List<DatasourceView> items) {
    }
}
