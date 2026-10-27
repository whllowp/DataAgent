package com.dataagent.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SqlDtos {

    private SqlDtos() {
    }

    public record ExecuteRequest(
            @NotNull UUID datasourceId,
            @NotBlank @Size(max = 100000) String sql,
            @Min(1) @Max(5000) Integer maxRows,
            @Min(1) @Max(60) Integer timeoutSeconds,
            Boolean includePlan,
            @Size(max = 2000) String problem,
            @Size(max = 500) String tags
    ) {
    }

    public record ExplainRequest(
            @NotNull UUID datasourceId,
            @NotBlank @Size(max = 100000) String sql
    ) {
    }

    public record ColumnMeta(String name, String label, String typeName, String javaType, int jdbcType) {
    }

    public record PlanInsight(String level, String code, String title, String detail) {
    }

    public record PlanView(String format, String raw, String summary, List<PlanInsight> insights, long explainMs) {
    }

    public record QueryResult(
            UUID queryId,
            String status,
            List<ColumnMeta> columns,
            List<List<Object>> rows,
            int rowCount,
            boolean truncated,
            long elapsedMs,
            long fetchMs,
            PlanView plan,
            String message,
            Instant executedAt
    ) {
    }

    public record ExplainResult(
            UUID queryId,
            PlanView plan
    ) {
    }

    public record ValidateResult(boolean valid, String message, String statementType) {
    }
}
