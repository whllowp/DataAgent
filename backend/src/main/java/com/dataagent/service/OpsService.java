package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.QueryExecution;
import com.dataagent.domain.SemanticSyncRun;
import com.dataagent.domain.SqlBenchmarkCase;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.repository.SemanticSyncRunRepository;
import com.dataagent.repository.SqlBenchmarkCaseRepository;
import com.dataagent.web.dto.OpsDtos;
import com.dataagent.web.dto.SemanticDtos;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class OpsService {

    private final QueryExecutionRepository executions;
    private final DatasourceService datasources;
    private final SemanticSyncRunRepository syncRuns;
    private final SqlBenchmarkCaseRepository benchmarks;
    private final DataAgentProperties properties;
    private final QueryResultCache queryCache;
    private final AuditService audit;

    public OpsService(QueryExecutionRepository executions,
                      DatasourceService datasources,
                      SemanticSyncRunRepository syncRuns,
                      SqlBenchmarkCaseRepository benchmarks,
                      DataAgentProperties properties,
                      QueryResultCache queryCache,
                      AuditService audit) {
        this.executions = executions;
        this.datasources = datasources;
        this.syncRuns = syncRuns;
        this.benchmarks = benchmarks;
        this.properties = properties;
        this.queryCache = queryCache;
        this.audit = audit;
    }

    public OpsDtos.OpsOverview overview() {
        List<OpsDtos.PoolStat> pools = datasources.poolStats();
        List<SemanticSyncRun> runs = syncRuns.findTop10ByOrderByStartedAtDesc();
        SemanticSyncRun last = runs.isEmpty() ? null : runs.get(0);
        SemanticDtos.SyncRunView lastSync = last == null ? null : syncView(last);

        Instant nextSync = null;
        if (last != null && last.getStartedAt() != null) {
            nextSync = last.getStartedAt().plusMillis(properties.getSemantic().getSyncIntervalMs());
        }

        List<SqlBenchmarkCase> benchmarkCases = benchmarks.findAll();
        Instant lastBenchmarkRun = benchmarkCases.stream()
                .map(SqlBenchmarkCase::getLastRunAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant nextBenchmarkRun = lastBenchmarkRun == null ? null
                : lastBenchmarkRun.plusMillis(properties.getBenchmark().getSyncIntervalMs());
        long failedLastRun = benchmarkCases.stream()
                .filter(item -> "ERROR".equalsIgnoreCase(item.getLastStatus()))
                .count();
        long slowLastRun = benchmarkCases.stream()
                .filter(item -> item.getLastDurationMs() != null
                        && item.getLastDurationMs() >= (item.getThresholdMs() == null ? 1000 : item.getThresholdMs()))
                .count();

        Instant startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long today = executions.countByCreatedAtAfter(startOfDay);
        long total = executions.count();
        long failed = executions.countByStatus("ERROR");
        Double average = executions.averageDuration();
        long p95 = p95(executions.allSuccessfulDurations());
        long slow = executions.countByDurationMsGreaterThanEqual(properties.getQuery().getAutoPlanThresholdMs());
        List<OpsDtos.ErrorRow> recentErrors = executions.findTop10ByStatusOrderByCreatedAtDesc("ERROR")
                .stream().map(this::errorRow).toList();

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        boolean degraded = failedLastRun > 0
                || (lastSync != null && "FAILED".equalsIgnoreCase(lastSync.status()))
                || pools.stream().anyMatch(pool -> pool.waiting() > 0);

        return new OpsDtos.OpsOverview(
                degraded ? "DEGRADED" : "UP",
                Instant.now(),
                uptime,
                today,
                total,
                failed,
                average == null ? 0 : average,
                p95,
                slow,
                pools.size(),
                pools,
                heap.getUsed(),
                heap.getMax(),
                threads,
                lastSync,
                nextSync,
                properties.getSemantic().getSyncIntervalMs(),
                new OpsDtos.BenchmarkStat(benchmarkCases.size(),
                        benchmarkCases.stream().filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus())).count(),
                        failedLastRun, slowLastRun, lastBenchmarkRun),
                nextBenchmarkRun,
                properties.getBenchmark().getSyncIntervalMs(),
                recentErrors,
                "/actuator/prometheus",
                queryCache.stats(),
                audit.overview()
        );
    }

    private OpsDtos.ErrorRow errorRow(QueryExecution execution) {
        return new OpsDtos.ErrorRow(execution.getId(), execution.getDatasourceId(), execution.getSqlText(),
                execution.getDurationMs(), execution.getErrorMessage(), execution.getCreatedAt());
    }

    private SemanticDtos.SyncRunView syncView(SemanticSyncRun run) {
        return new SemanticDtos.SyncRunView(run.getId(), run.getTriggerType(), run.getStatus(),
                run.getStartedAt(), run.getFinishedAt(), run.getScannedAssets(), run.getScannedKnowledge(),
                run.getCreatedLinks(), run.getUpdatedLinks(), run.getRemovedLinks(), run.getMessage());
    }

    private static long p95(List<Long> durations) {
        if (durations == null || durations.isEmpty()) {
            return 0;
        }
        List<Long> sorted = durations.stream().filter(Objects::nonNull).sorted().toList();
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
