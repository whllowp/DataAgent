package com.dataagent.service;

import com.dataagent.domain.SqlBenchmarkCase;
import com.dataagent.repository.SqlBenchmarkCaseRepository;
import com.dataagent.web.dto.BenchmarkDtos;
import com.dataagent.web.dto.DatasourceDtos;
import com.dataagent.web.dto.SqlDtos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final SqlBenchmarkCaseRepository cases;
    private final DatasourceService datasources;
    private final JdbcQueryService queries;
    private final MeterRegistry meterRegistry;

    public BenchmarkService(SqlBenchmarkCaseRepository cases,
                            DatasourceService datasources,
                            JdbcQueryService queries,
                            MeterRegistry meterRegistry) {
        this.cases = cases;
        this.datasources = datasources;
        this.queries = queries;
        this.meterRegistry = meterRegistry;
    }

    public List<BenchmarkDtos.View> list() {
        return cases.findAllByOrderByUpdatedAtDesc().stream().map(this::view).toList();
    }

    public BenchmarkDtos.View get(UUID id) {
        return view(required(id));
    }

    public BenchmarkDtos.View create(BenchmarkDtos.Request request) {
        if (cases.findByNameIgnoreCase(request.name()).isPresent()) {
            throw new IllegalArgumentException("基准用例名称已存在: " + request.name());
        }
        SqlBenchmarkCase item = new SqlBenchmarkCase();
        apply(item, request);
        return view(cases.save(item));
    }

    public BenchmarkDtos.View update(UUID id, BenchmarkDtos.Request request) {
        SqlBenchmarkCase item = required(id);
        cases.findByNameIgnoreCase(request.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("基准用例名称已存在: " + request.name());
                });
        apply(item, request);
        return view(cases.save(item));
    }

    public void delete(UUID id) {
        cases.delete(required(id));
    }

    public BenchmarkDtos.RunResult run(UUID id) {
        SqlBenchmarkCase item = required(id);
        UUID datasourceId = item.getDatasourceId();
        if (datasourceId == null) {
            datasourceId = firstDatasourceId();
            item.setDatasourceId(datasourceId);
        }
        long start = System.nanoTime();
        String status = "ERROR";
        String error = null;
        SqlDtos.QueryResult result = null;
        try {
            result = queries.execute(datasourceId, item.getSqlText(), 100, null, false, false,
                    "sre-benchmark", item.getDescription(), benchmarkTags(item));
            status = result.status();
            error = result.message();
        } catch (RuntimeException ex) {
            error = ex.getMessage();
            log.warn("Benchmark {} failed before execution: {}", item.getName(), error);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        item.setLastRunAt(Instant.now());
        item.setLastDurationMs(elapsed);
        item.setLastStatus(status);
        item.setLastError(error);
        if (item.getBaselineMs() == null && "SUCCESS".equals(status)) {
            item.setBaselineMs(elapsed);
        }
        cases.save(item);

        meterRegistry.timer("dataagent.benchmark.run.duration",
                "status", status,
                "domain", safe(item.getDomain())).record(Duration.ofMillis(elapsed));
        Counter.builder("dataagent.benchmark.runs")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        return new BenchmarkDtos.RunResult(view(item), result);
    }

    public BenchmarkDtos.RunAllResult runAll() {
        List<SqlBenchmarkCase> active = cases.findByStatusOrderByNameAsc("ACTIVE");
        int succeeded = 0;
        int failed = 0;
        for (SqlBenchmarkCase item : active) {
            try {
                BenchmarkDtos.RunResult result = run(item.getId());
                if ("SUCCESS".equals(result.benchmark().lastStatus())) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Benchmark {} failed: {}", item.getName(), ex.getMessage());
            }
        }
        return new BenchmarkDtos.RunAllResult(active.size(), succeeded, failed);
    }

    private UUID firstDatasourceId() {
        return datasources.list().stream()
                .findFirst()
                .map(DatasourceDtos.DatasourceView::id)
                .orElseThrow(() -> new IllegalArgumentException("还没有可用数据源，无法运行基准用例"));
    }

    private void apply(SqlBenchmarkCase item, BenchmarkDtos.Request request) {
        item.setName(request.name().trim());
        item.setDomain(trim(request.domain()));
        item.setDescription(trim(request.description()));
        item.setSqlText(request.sqlText().trim());
        item.setDatasourceId(request.datasourceId());
        item.setBaselineMs(request.baselineMs());
        item.setThresholdMs(request.thresholdMs());
        item.setTags(trim(request.tags()));
        item.setStatus(request.status() == null || request.status().isBlank()
                ? "ACTIVE" : request.status().trim().toUpperCase());
        if (item.getDatasourceId() != null) {
            datasources.getRequired(item.getDatasourceId());
        }
    }

    private SqlBenchmarkCase required(UUID id) {
        return cases.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("基准用例不存在: " + id));
    }

    private BenchmarkDtos.View view(SqlBenchmarkCase item) {
        return new BenchmarkDtos.View(item.getId(), item.getName(), item.getDomain(),
                item.getDescription(), item.getSqlText(), item.getDatasourceId(),
                item.getBaselineMs(), item.getThresholdMs(), item.getTags(), item.getStatus(),
                item.getLastRunAt(), item.getLastDurationMs(), item.getLastStatus(), item.getLastError(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    private String benchmarkTags(SqlBenchmarkCase item) {
        String tags = "SRE,benchmark";
        if (item.getTags() != null && !item.getTags().isBlank()) {
            tags += "," + item.getTags();
        }
        return tags;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
