package com.dataagent.service;

import com.dataagent.domain.SqlOptimizationRun;
import com.dataagent.repository.SqlOptimizationRunRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.sql.SqlGuard;
import com.dataagent.sql.SqlRewriter;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.OptimizeDtos;
import com.dataagent.web.dto.SqlDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ten-round SQL optimization evolution. Each round generates a safe rewrite of
 * the best-so-far candidate, re-benchmarks it, and keeps the fastest variant;
 * the score advances monotonically with each completed round.
 */
@Service
public class SqlOptimizationLabService {

    private static final Logger log = LoggerFactory.getLogger(SqlOptimizationLabService.class);

    private final SqlGuard guard;
    private final SqlRewriter rewriter;
    private final JdbcQueryService queries;
    private final SqlOptimizationRunRepository runs;

    public SqlOptimizationLabService(SqlGuard guard, SqlRewriter rewriter, JdbcQueryService queries,
                                     SqlOptimizationRunRepository runs) {
        this.guard = guard;
        this.rewriter = rewriter;
        this.queries = queries;
        this.runs = runs;
    }

    @Transactional
    public OptimizeDtos.EvolutionView evolve(UUID datasourceId, String rawSql, int rounds) {
        SqlGuard.GuardedSql guarded = guard.guardForExecution(rawSql);
        int totalRounds = Math.max(1, Math.min(rounds, 50));
        Instant startedAt = Instant.now();

        long baseline = benchmark(datasourceId, guarded.sql());
        long best = baseline;
        String bestSql = guarded.sql();
        List<OptimizeDtos.RoundResult> roundResults = new ArrayList<>();
        int score = 0;

        for (int round = 1; round <= totalRounds; round++) {
            String candidate = rewriter.rewrite(bestSql).rewrittenSql();
            long candidateMs = candidate.equals(bestSql)
                    ? benchmark(datasourceId, bestSql)
                    : benchmark(datasourceId, candidate);
            boolean improved = candidateMs < best;
            if (improved) {
                best = candidateMs;
                bestSql = candidate;
            }
            score = Math.round(round * 100f / totalRounds);
            roundResults.add(new OptimizeDtos.RoundResult(round, candidate, candidateMs, improved));
        }

        Double improvement = improvement(baseline, best);
        String verdict = verdict(baseline, best);
        SqlOptimizationRun run = new SqlOptimizationRun();
        run.setTenantId(AuthContext.currentOrSystem().tenantId());
        run.setDatasourceId(datasourceId);
        run.setOriginalSql(guarded.sql());
        run.setBestSql(bestSql);
        run.setBaselineMs(baseline);
        run.setBestMs(best);
        run.setImprovementPct(improvement);
        run.setScore(score);
        run.setRounds(totalRounds);
        run.setVerdict(verdict);
        run.setCreatedAt(startedAt);
        runs.save(run);
        log.info("SQL optimization run {} finished: baseline {}ms -> best {}ms ({}%), rounds {}, score {}",
                run.getId(), baseline, best, improvement, totalRounds, score);
        return new OptimizeDtos.EvolutionView(run.getId(), datasourceId, guarded.sql(), bestSql,
                baseline, best, improvement, score, totalRounds, verdict, roundResults, startedAt);
    }

    private long benchmark(UUID datasourceId, String sql) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            SqlDtos.QueryResult result = queries.execute(datasourceId, sql, 100, null, false, false,
                    false, "optimize-lab", "优化十轮基准", "optimize-lab", true);
            if ("ERROR".equals(result.status())) {
                return -1;
            }
            best = Math.min(best, result.elapsedMs());
        }
        return best;
    }

    private Double improvement(long baseline, long best) {
        if (baseline <= 0 || best < 0) {
            return null;
        }
        return Math.round(((baseline - best) * 10000.0 / baseline)) / 100.0;
    }

    private String verdict(long baseline, long best) {
        if (best < 0) {
            return "FAILED";
        }
        Double improvement = improvement(baseline, best);
        if (improvement != null && improvement >= 20) {
            return "FASTER";
        }
        if (improvement != null && improvement <= -20) {
            return "SLOWER";
        }
        return "SIMILAR";
    }
}
