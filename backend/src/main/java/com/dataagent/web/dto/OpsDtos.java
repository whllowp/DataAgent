package com.dataagent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OpsDtos {

    private OpsDtos() {
    }

    public record PoolStat(
            UUID datasourceId,
            String name,
            String dialect,
            int active,
            int idle,
            int total,
            int waiting
    ) {
    }

    public record ErrorRow(
            UUID queryId,
            UUID datasourceId,
            String sqlText,
            long durationMs,
            String errorMessage,
            Instant createdAt
    ) {
    }

    public record BenchmarkStat(
            long total,
            long active,
            long failedLastRun,
            long slowLastRun,
            Instant lastRunAt
    ) {
    }

    public record OpsOverview(
            String status,
            Instant time,
            long uptimeSeconds,
            long queriesToday,
            long totalQueries,
            long failedQueries,
            double averageDurationMs,
            long p95DurationMs,
            long slowQueries,
            long datasourceCount,
            List<PoolStat> pools,
            long heapUsedBytes,
            long heapMaxBytes,
            int threadCount,
            SemanticDtos.SyncRunView lastSync,
            Instant nextScheduledSync,
            long semanticSyncIntervalMs,
            BenchmarkStat benchmarks,
            Instant nextScheduledBenchmark,
            long benchmarkIntervalMs,
            List<ErrorRow> recentErrors,
            String prometheusPath
    ) {
    }
}
