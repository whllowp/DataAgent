package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SqlAssetDtos {

    private SqlAssetDtos() {
    }

    public record SqlAssetView(
            UUID id,
            String sqlText,
            String businessProblem,
            String domain,
            String tags,
            UUID datasourceId,
            String author,
            String source,
            int executionCount,
            int successCount,
            int failureCount,
            Long avgDurationMs,
            Long lastDurationMs,
            UUID knowledgeId,
            Instant firstSeen,
            Instant lastSeen,
            Instant semanticSyncedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 100000) String sql,
            @Size(max = 8000) String businessProblem,
            @Size(max = 120) String domain,
            @Size(max = 500) String tags,
            UUID datasourceId,
            @Size(max = 120) String author
    ) {
    }

    public record ImportRequest(
            @NotEmpty @Size(max = 500) List<SaveRequest> items
    ) {
    }

    public record AssetPage(
            List<SqlAssetView> items,
            long total,
            int page,
            int size,
            int totalPages
    ) {
    }

    public record CacheStats(long totalAssets, long withBusinessProblem, long withKnowledge, long linkedToSemantic) {
    }
}
