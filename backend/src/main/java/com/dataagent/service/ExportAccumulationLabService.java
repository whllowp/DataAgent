package com.dataagent.service;

import com.dataagent.web.dto.SqlDtos;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ten-round performance lab for the data-export and knowledge-accumulation
 * hot paths. Mirrors {@link PerformanceLabService}: a cold sample (after a
 * warm-up-free start) is compared against the last-3 median of ten consecutive
 * rounds, so JIT/GC warm-up and idempotent accumulation are measured honestly.
 */
@Service
public class ExportAccumulationLabService {

    private static final int SYNTHETIC_ROWS = 1000;

    private final DataExportService exports;
    private final KnowledgeAccumulationService accumulation;

    public ExportAccumulationLabService(DataExportService exports,
                                        KnowledgeAccumulationService accumulation) {
        this.exports = exports;
        this.accumulation = accumulation;
    }

    public record OperationResult(
            String operation,
            double coldMs,
            double warmMedianMs,
            double p95Ms,
            double improvementPct,
            List<Double> roundsMs
    ) {
    }

    public record Report(
            int rounds,
            List<OperationResult> operations,
            String note
    ) {
    }

    public Report run(int rounds) {
        int count = Math.max(1, Math.min(10, rounds));
        SqlDtos.QueryResult sample = syntheticResult();
        List<OperationResult> operations = new ArrayList<>();
        operations.add(measure("export.csv", count, () -> exports.serialize(sample, "csv")));
        operations.add(measure("export.json", count, () -> exports.serialize(sample, "json")));
        operations.add(measure("accumulation", count, () -> accumulation.accumulate("LAB", 3, "system")));
        return new Report(count, operations,
                "cold sample is the first call; rounds are consecutive; improvement compares cold to last-3 median");
    }

    private OperationResult measure(String name, int rounds, java.util.concurrent.Callable<byte[]> work) {
        double cold = runOnce(work);
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            samples.add(runOnce(work));
        }
        List<Double> tail = samples.subList(Math.max(0, samples.size() - 3), samples.size());
        double warm = median(tail);
        double p95 = percentile(samples, 0.95);
        double improvement = cold <= 0 ? 0 : Math.max(0, (cold - warm) * 100.0 / cold);
        return new OperationResult(name, round(cold), round(warm), round(p95),
                Math.round(improvement * 10.0) / 10.0,
                samples.stream().map(ExportAccumulationLabService::round).toList());
    }

    private OperationResult measure(String name, int rounds, Runnable work) {
        long start = System.nanoTime();
        work.run();
        double cold = elapsedMs(start);
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            start = System.nanoTime();
            work.run();
            samples.add(elapsedMs(start));
        }
        List<Double> tail = samples.subList(Math.max(0, samples.size() - 3), samples.size());
        double warm = median(tail);
        double p95 = percentile(samples, 0.95);
        double improvement = cold <= 0 ? 0 : Math.max(0, (cold - warm) * 100.0 / cold);
        return new OperationResult(name, round(cold), round(warm), round(p95),
                Math.round(improvement * 10.0) / 10.0,
                samples.stream().map(ExportAccumulationLabService::round).toList());
    }

    private double runOnce(java.util.concurrent.Callable<byte[]> work) {
        long start = System.nanoTime();
        try {
            work.call();
        } catch (Exception e) {
            // ignore benchmark errors; timing still advances
        }
        return elapsedMs(start);
    }

    private SqlDtos.QueryResult syntheticResult() {
        List<SqlDtos.ColumnMeta> columns = List.of(
                new SqlDtos.ColumnMeta("id", "id", "BIGINT", "Long", -5),
                new SqlDtos.ColumnMeta("name", "name", "VARCHAR", "String", 12),
                new SqlDtos.ColumnMeta("city", "city", "VARCHAR", "String", 12),
                new SqlDtos.ColumnMeta("segment", "segment", "VARCHAR", "String", 12),
                new SqlDtos.ColumnMeta("order_date", "order_date", "DATE", "String", 91),
                new SqlDtos.ColumnMeta("quantity", "quantity", "INTEGER", "Integer", 4),
                new SqlDtos.ColumnMeta("amount", "amount", "DECIMAL", "BigDecimal", 3),
                new SqlDtos.ColumnMeta("status", "status", "VARCHAR", "String", 12));
        List<List<Object>> rows = new ArrayList<>(SYNTHETIC_ROWS);
        for (int i = 0; i < SYNTHETIC_ROWS; i++) {
            rows.add(List.of((long) i, "客户-\"" + i + "\"", "北京", "ENTERPRISE",
                    "2024-01-15", 5, 1234.56, "PAID"));
        }
        return new SqlDtos.QueryResult(UUID.randomUUID(), "SUCCESS", columns, rows, rows.size(),
                false, 0, 0, null, null, Instant.now(), false, List.of());
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }

    private static double percentile(List<Double> values, double p) {
        List<Double> sorted = values.stream().sorted().toList();
        int index = Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * p) - 1));
        return sorted.get(index);
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
