package com.dataagent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Local performance lab for the memory/ontology hot paths.
 *
 * <p>The benchmark deliberately clears the in-process embedding and graph
 * caches before the cold sample, then records ten consecutive rounds. This
 * separates "cold" work from "warm" work instead of hiding both behind one
 * average. It is a development regression tool, not a production load test.</p>
 */
@Service
public class PerformanceLabService {

    private final MemoryEvolutionService memory;
    private final OntologyEvolutionService ontology;
    private final EmbeddingService embeddings;

    public PerformanceLabService(MemoryEvolutionService memory, OntologyEvolutionService ontology,
                                 EmbeddingService embeddings) {
        this.memory = memory;
        this.ontology = ontology;
        this.embeddings = embeddings;
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
        List<OperationResult> operations = new ArrayList<>();
        operations.add(measure("memory.retrieval+compress", count, () -> {
            memory.retrievalEval(10);
            memory.compress(null, 1600);
        }, () -> {
            embeddings.clearCache();
            memory.clearCaches();
        }));
        operations.add(measure("ontology.answerable+paths", count, () -> {
            var answer = ontology.answerableV2("CUSTOMERS");
            var terms = answer.base().terms();
            if (terms.size() >= 2) {
                ontology.bestPaths(terms.get(0).id(), terms.get(1).id(), 5);
            }
        }, ontology::clearCaches));
        return new Report(count, operations,
                "cold sample clears caches; rounds are consecutive; improvement compares cold to last-3 median");
    }

    private OperationResult measure(String name, int rounds, Runnable work, Runnable clear) {
        clear.run();
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
                samples.stream().map(PerformanceLabService::round).toList());
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
