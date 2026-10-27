package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class HistoryDtos {

    private HistoryDtos() {
    }

    public record HistoryView(
            UUID id,
            UUID datasourceId,
            String datasourceName,
            String dialect,
            String userId,
            String kind,
            String sqlText,
            String status,
            long durationMs,
            long fetchMs,
            int rowCount,
            boolean truncated,
            String errorMessage,
            String planRaw,
            String planFormat,
            String problem,
            String tags,
            boolean solved,
            UUID knowledgeId,
            Instant createdAt
    ) {
    }

    public record HistoryPage(List<HistoryView> items, long total, int page, int size, int totalPages) {
    }

    public record SolveRequest(
            @Size(max = 300) String title,
            @Size(max = 2000) String problem,
            @Size(max = 8000) String rootCause,
            @Size(max = 8000) String optimizationSteps,
            @Size(max = 100000) String optimizedSql,
            @Size(max = 500) String tags,
            Long afterDurationMs
    ) {
    }

    public record NoteRequest(
            @Size(max = 2000) String problem,
            @Size(max = 500) String tags
    ) {
    }

    public record SolveResult(UUID queryId, UUID knowledgeId, KnowledgeDtos.KnowledgeView knowledge) {
    }

    public record HistoryDetail(HistoryView execution, List<KnowledgeDtos.KnowledgeView> knowledge) {
    }

    public record QueryStats(
            long totalQueries,
            long queriesToday,
            double averageDurationMs,
            long p95DurationMs,
            long slowQueries,
            long solvedQueries,
            long knowledgeCount,
            long datasourceCount,
            List<HistoryView> recentQueries,
            List<HistoryView> slowestQueries
    ) {
    }

    public record TextBlock(String text) {
    }
}
