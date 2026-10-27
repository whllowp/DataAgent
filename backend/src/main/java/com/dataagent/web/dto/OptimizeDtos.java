package com.dataagent.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OptimizeDtos {

    private OptimizeDtos() {
    }

    public record AnalyzeRequest(
            @NotNull UUID datasourceId,
            @NotBlank @Size(max = 100000) String sql,
            Boolean includePlan
    ) {
    }

    public record Finding(
            String code,
            String severity,
            String category,
            String title,
            String detail,
            String suggestion,
            String evidence
    ) {
    }

    public record AnalysisView(
            UUID datasourceId,
            String dialect,
            String sql,
            List<Finding> findings,
            SqlDtos.PlanView plan,
            RunMetrics metrics,
            String promptContext,
            List<String> guardrails
    ) {
    }

    public record RunMetrics(long elapsedMs, int rowCount, boolean truncated, String status, String message) {
    }

    public record CompareRequest(
            @NotNull UUID datasourceId,
            @NotBlank @Size(max = 100000) String originalSql,
            @NotBlank @Size(max = 100000) String candidateSql,
            @Min(1) @Max(5) Integer runs,
            Boolean execute,
            String notes
    ) {
    }

    public record CompareView(
            UUID experimentId,
            UUID datasourceId,
            SqlDtos.PlanView originalPlan,
            SqlDtos.PlanView candidatePlan,
            Long originalMs,
            Long candidateMs,
            Double improvementPercent,
            String verdict,
            List<Finding> originalFindings,
            List<Finding> candidateFindings,
            String notes,
            List<String> warnings,
            Instant createdAt
    ) {
    }

    public record PromptView(String prompt, List<String> guardrails) {
    }
}
