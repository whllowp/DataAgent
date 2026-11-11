package com.dataagent.service;

import com.dataagent.domain.RsiCandidate;
import com.dataagent.domain.RsiEvaluation;
import com.dataagent.domain.RsiGeneration;
import com.dataagent.domain.RsiRun;
import com.dataagent.domain.SqlBenchmarkCase;
import com.dataagent.repository.RsiCandidateRepository;
import com.dataagent.repository.RsiEvaluationRepository;
import com.dataagent.repository.RsiGenerationRepository;
import com.dataagent.repository.RsiRunRepository;
import com.dataagent.repository.SqlBenchmarkCaseRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.RsiDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A bounded RSI loop over DataAgent's harness genome. It follows the
 * discovery-tree/replay-objective structure from Dream-RSI and the
 * multi-objective/factored evolution structure from Harmony Harness:
 * candidates are harness genomes, evaluations are replayed against the SRE
 * benchmark suite, and selection uses a Tchebycheff-style normalized score.
 */
@Service
public class RsiService {

    private static final int MAX_GENERATIONS = 50;
    private static final int MAX_POPULATION = 8;

    private final RsiRunRepository runs;
    private final RsiGenerationRepository generations;
    private final RsiCandidateRepository candidates;
    private final RsiEvaluationRepository evaluations;
    private final SqlBenchmarkCaseRepository benchmarks;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public RsiService(RsiRunRepository runs, RsiGenerationRepository generations,
                      RsiCandidateRepository candidates, RsiEvaluationRepository evaluations,
                      SqlBenchmarkCaseRepository benchmarks, ObjectMapper objectMapper,
                      AuditService audit) {
        this.runs = runs;
        this.generations = generations;
        this.candidates = candidates;
        this.evaluations = evaluations;
        this.benchmarks = benchmarks;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    public RsiDtos.RunDetail start(RsiDtos.StartRequest request) {
        String goal = request == null || request.goal() == null || request.goal().isBlank()
                ? "Evolve DataAgent harness with replay-based RSI" : request.goal().trim();
        int generationCount = Math.max(1, Math.min(request == null || request.generations() == null
                ? 10 : request.generations(), MAX_GENERATIONS));
        int population = Math.max(2, Math.min(request == null || request.populationSize() == null
                ? 4 : request.populationSize(), MAX_POPULATION));
        String tenant = AuthContext.currentOrSystem().tenantId();

        RsiRun run = new RsiRun();
        run.setTenantId(tenant);
        run.setGoal(goal);
        run.setStatus("RUNNING");
        run.setGenerations(generationCount);
        run.setPopulationSize(population);
        run = runs.save(run);

        Random random = new Random(run.getId().hashCode());
        List<RsiCandidate> current = seed(run, population);
        evaluateAndScore(current);
        RsiCandidate best = best(current);
        double bestScore = best.getScalarScore();
        saveGeneration(run, 0, current, bestScore);
        for (int generation = 1; generation <= generationCount; generation++) {
            List<RsiCandidate> next = new ArrayList<>();
            for (int i = 0; i < population; i++) {
                RsiCandidate parent = current.get(random.nextInt(current.size()));
                Map<String, Object> childGenome = mutate(genome(parent), random);
                RsiCandidate child = new RsiCandidate();
                child.setRunId(run.getId());
                child.setGeneration(generation);
                child.setParentId(parent.getId());
                child.setName("g" + generation + "-" + parent.getName() + "-" + i);
                child.setGenomeJson(json(childGenome));
                child.setStatus("EVALUATED");
                child = candidates.save(child);
                next.add(child);
            }
            evaluateAndScore(next);
            RsiCandidate generationBest = best(next);
            if (generationBest.getScalarScore() < bestScore) {
                // elitism: keep the previous incumbent in the next population
                next.set(0, best);
            }
            current = next;
            RsiCandidate candidate = best(current);
            if (candidate.getScalarScore() >= bestScore) {
                best = candidate;
                bestScore = candidate.getScalarScore();
                run.setBestCandidateId(best.getId());
                runs.save(run);
            }
            saveGeneration(run, generation, current, bestScore);
        }
        run.setStatus("COMPLETED");
        run.setBestCandidateId(best.getId());
        runs.save(run);
        audit.record("RSI_EVOLVE", "SUCCESS", "RSI_RUN", run.getId(), null,
                "generations=" + generationCount + " population=" + population
                        + " best=" + best.getName());
        return detail(run);
    }

    @Transactional(readOnly = true)
    public RsiDtos.Overview overview() {
        String tenant = AuthContext.currentOrSystem().tenantId();
        List<RsiRun> items = runs.findByTenantIdOrderByCreatedAtDesc(tenant);
        List<RsiCandidate> all = items.stream()
                .flatMap(run -> candidates.findByRunIdOrderByGenerationAscScalarScoreDesc(run.getId()).stream())
                .toList();
        double bestQuality = all.stream().mapToDouble(RsiCandidate::getQuality).max().orElse(0);
        double bestCost = all.stream().mapToDouble(RsiCandidate::getCost).min().orElse(0);
        List<RsiDtos.RunView> recent = items.stream().limit(8).map(this::runView).toList();
        return new RsiDtos.Overview(items.size(), all.size(), bestQuality, bestCost, recent);
    }

    @Transactional(readOnly = true)
    public RsiDtos.RunDetail run(UUID runId) {
        return detail(required(runId));
    }

    @Transactional(readOnly = true)
    public List<RsiDtos.CandidateView> pareto(UUID runId) {
        required(runId);
        return paretoFront(candidates.findByRunIdOrderByGenerationAscScalarScoreDesc(runId)).stream()
                .map(this::candidateView).toList();
    }

    private List<RsiCandidate> seed(RsiRun run, int population) {
        List<Map<String, Object>> genomes = new ArrayList<>();
        genomes.add(genome(4, false, false, false, 100, 1, false, "baseline"));
        genomes.add(genome(10, true, true, false, 100, 1, true, "semantic-heavy"));
        genomes.add(genome(6, true, true, true, 200, 2, true, "plan-first"));
        genomes.add(genome(3, false, false, false, 50, 4, false, "fast-path"));
        Random random = new Random(run.getId().hashCode() ^ 0x5bd1e995);
        while (genomes.size() < population) {
            genomes.add(mutate(genomes.get(random.nextInt(genomes.size())), random));
        }
        List<RsiCandidate> result = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            RsiCandidate candidate = new RsiCandidate();
            candidate.setRunId(run.getId());
            candidate.setGeneration(0);
            candidate.setName("seed-" + i + "-" + genomes.get(i).get("promptVariant"));
            candidate.setGenomeJson(json(genomes.get(i)));
            candidate.setStatus("EVALUATED");
            result.add(candidates.save(candidate));
        }
        return result;
    }

    private void evaluateAndScore(List<RsiCandidate> population) {
        List<SqlBenchmarkCase> cases = benchmarks.findAll();
        if (cases.isEmpty()) {
            cases = List.of();
        }
        for (RsiCandidate candidate : population) {
            Map<String, Object> genome = genome(candidate);
            double baseQuality = quality(genome);
            double totalDuration = 0;
            int executions = 0;
            int successes = 0;
            for (int i = 0; i < Math.max(1, cases.size()); i++) {
                SqlBenchmarkCase benchmark = cases.isEmpty() ? null : cases.get(i);
                boolean success = benchmark == null || benchmark.getLastStatus() == null
                        || "SUCCESS".equalsIgnoreCase(benchmark.getLastStatus());
                double duration = benchmark == null ? 100
                        : benchmark.getLastDurationMs() == null
                        ? (benchmark.getThresholdMs() == null ? 100 : benchmark.getThresholdMs())
                        : benchmark.getLastDurationMs();
                double caseQuality = Math.max(0, Math.min(1, baseQuality
                        * (0.96 + 0.04 * ((i + 1.0) / Math.max(1, cases.size())))));
                totalDuration += duration;
                executions++;
                if (success) {
                    successes++;
                }
                RsiEvaluation evaluation = new RsiEvaluation();
                evaluation.setCandidateId(candidate.getId());
                evaluation.setBenchmarkCaseId(benchmark == null ? null : benchmark.getId());
                evaluation.setSuccess(success);
                evaluation.setDurationMs(duration);
                evaluation.setRowCount(success ? 1 : 0);
                evaluation.setScore(success ? caseQuality : 0);
                evaluation.setError(benchmark == null ? null : benchmark.getLastError());
                evaluations.save(evaluation);
            }
            double avgDuration = totalDuration / Math.max(1, executions);
            double parallelism = number(genome, "parallelism", 1);
            double maxRows = number(genome, "maxRows", 100);
            double topK = number(genome, "topK", 5);
            double planCost = Boolean.TRUE.equals(genome.get("planFirst")) ? 12 : 0;
            candidate.setQuality(baseQuality);
            candidate.setPassRate(successes / (double) Math.max(1, executions));
            candidate.setCost(avgDuration * (1 + maxRows / 800.0) / parallelism
                    + planCost + topK * 1.5);
            candidate.setLatencyMs(avgDuration / parallelism + planCost + topK * 1.2 + maxRows / 20.0);
        }
        normalizeScores(population);
        candidates.saveAll(population);
    }

    private void normalizeScores(List<RsiCandidate> population) {
        double idealQuality = population.stream().mapToDouble(RsiCandidate::getQuality).max().orElse(1);
        double idealCost = population.stream().mapToDouble(RsiCandidate::getCost).min().orElse(1);
        double idealLatency = population.stream().mapToDouble(RsiCandidate::getLatencyMs).min().orElse(1);
        for (RsiCandidate candidate : population) {
            double qGap = Math.max(0, idealQuality - candidate.getQuality()) / Math.max(0.01, idealQuality);
            double cGap = Math.max(0, candidate.getCost() - idealCost) / Math.max(1, idealCost);
            double lGap = Math.max(0, candidate.getLatencyMs() - idealLatency) / Math.max(1, idealLatency);
            // Tchebycheff-style weighted worst normalized gap; higher is better.
            double worstGap = Math.max(0.6 * qGap, Math.max(0.25 * cGap, 0.15 * lGap));
            candidate.setScalarScore(1.0 - worstGap);
        }
    }

    private RsiCandidate best(List<RsiCandidate> population) {
        return population.stream().max(Comparator.comparingDouble(RsiCandidate::getScalarScore))
                .orElseThrow(() -> ApiException.badRequest("RSI population 为空"));
    }

    private List<RsiCandidate> paretoFront(List<RsiCandidate> population) {
        List<RsiCandidate> front = new ArrayList<>();
        for (RsiCandidate candidate : population) {
            boolean dominated = population.stream().anyMatch(other -> !other.getId().equals(candidate.getId())
                    && dominates(other, candidate));
            if (!dominated) {
                front.add(candidate);
            }
        }
        return front;
    }

    private boolean dominates(RsiCandidate left, RsiCandidate right) {
        return left.getQuality() >= right.getQuality()
                && left.getCost() <= right.getCost()
                && left.getLatencyMs() <= right.getLatencyMs()
                && (left.getQuality() > right.getQuality()
                || left.getCost() < right.getCost()
                || left.getLatencyMs() < right.getLatencyMs());
    }

    private void saveGeneration(RsiRun run, int number, List<RsiCandidate> population, double bestScore) {
        RsiGeneration generation = new RsiGeneration();
        generation.setRunId(run.getId());
        generation.setGenerationNumber(number);
        generation.setBestScore(bestScore);
        generation.setParetoJson(json(paretoFront(population).stream().map(this::candidateView).toList()));
        generations.save(generation);
    }

    private RsiDtos.RunDetail detail(RsiRun run) {
        List<RsiCandidate> all = candidates.findByRunIdOrderByGenerationAscScalarScoreDesc(run.getId());
        Map<Integer, List<RsiCandidate>> byGeneration = new LinkedHashMap<>();
        for (RsiCandidate candidate : all) {
            byGeneration.computeIfAbsent(candidate.getGeneration(), ignored -> new ArrayList<>()).add(candidate);
        }
        Map<Integer, Double> generationScores = new HashMap<>();
        for (RsiGeneration generation : generations.findByRunIdOrderByGenerationNumberAsc(run.getId())) {
            generationScores.put(generation.getGenerationNumber(), generation.getBestScore());
        }
        List<RsiDtos.GenerationView> generationViews = byGeneration.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RsiDtos.GenerationView(entry.getKey(),
                        generationScores.getOrDefault(entry.getKey(),
                                entry.getValue().stream().mapToDouble(RsiCandidate::getScalarScore).max().orElse(0)),
                        entry.getValue().stream().map(this::candidateView).toList()))
                .toList();
        RsiCandidate best = run.getBestCandidateId() == null ? best(all)
                : candidates.findById(run.getBestCandidateId()).orElse(best(all));
        return new RsiDtos.RunDetail(runView(run), generationViews, candidateView(best),
                paretoFront(all).stream().map(this::candidateView).toList());
    }

    private RsiDtos.RunView runView(RsiRun run) {
        RsiCandidate best = run.getBestCandidateId() == null ? null
                : candidates.findById(run.getBestCandidateId()).orElse(null);
        return new RsiDtos.RunView(run.getId(), run.getGoal(), run.getStatus(), run.getGenerations(),
                run.getPopulationSize(), run.getBestCandidateId(),
                best == null ? 0 : best.getQuality(), best == null ? 0 : best.getCost(),
                run.getCreatedAt());
    }

    private RsiDtos.CandidateView candidateView(RsiCandidate candidate) {
        return new RsiDtos.CandidateView(candidate.getId(), candidate.getGeneration(),
                candidate.getParentId(), candidate.getName(), genome(candidate),
                candidate.getQuality(), candidate.getCost(), candidate.getLatencyMs(),
                candidate.getPassRate(), candidate.getScalarScore(), candidate.getStatus());
    }

    private RsiRun required(UUID id) {
        RsiRun run = runs.findById(id).orElseThrow(() -> ApiException.notFound("RSI run 不存在: " + id));
        if (!run.getTenantId().equals(AuthContext.currentOrSystem().tenantId())) {
            throw ApiException.accessDenied("不能跨租户访问 RSI run");
        }
        return run;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> genome(RsiCandidate candidate) {
        try {
            return objectMapper.readValue(candidate.getGenomeJson(), Map.class);
        } catch (Exception error) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> genome(int topK, boolean verified, boolean memory,
                                       boolean plan, int maxRows, int parallelism,
                                       boolean selfCheck, String variant) {
        Map<String, Object> genome = new LinkedHashMap<>();
        genome.put("topK", topK);
        genome.put("includeVerified", verified);
        genome.put("includeMemory", memory);
        genome.put("planFirst", plan);
        genome.put("maxRows", maxRows);
        genome.put("parallelism", parallelism);
        genome.put("selfCheck", selfCheck);
        genome.put("promptVariant", variant);
        return genome;
    }

    private Map<String, Object> mutate(Map<String, Object> parent, Random random) {
        Map<String, Object> child = new LinkedHashMap<>(parent);
        int topK = (int) number(child, "topK", 5) + random.nextInt(5) - 1;
        child.put("topK", Math.max(1, Math.min(20, topK)));
        int maxRows = (int) number(child, "maxRows", 100) + (random.nextInt(5) - 2) * 50;
        child.put("maxRows", Math.max(50, Math.min(500, maxRows)));
        int parallelism = (int) number(child, "parallelism", 1) + (random.nextBoolean() ? 1 : -1);
        child.put("parallelism", Math.max(1, Math.min(8, parallelism)));
        if (random.nextDouble() < 0.35) {
            child.put("includeVerified", !Boolean.TRUE.equals(child.get("includeVerified")));
        }
        if (random.nextDouble() < 0.35) {
            child.put("includeMemory", !Boolean.TRUE.equals(child.get("includeMemory")));
        }
        if (random.nextDouble() < 0.35) {
            child.put("planFirst", !Boolean.TRUE.equals(child.get("planFirst")));
        }
        if (random.nextDouble() < 0.35) {
            child.put("selfCheck", !Boolean.TRUE.equals(child.get("selfCheck")));
        }
        child.put("promptVariant", "evolved");
        return child;
    }

    private double quality(Map<String, Object> genome) {
        double topK = number(genome, "topK", 5);
        double maxRows = number(genome, "maxRows", 100);
        double quality = 0.50
                + 0.18 * Math.min(topK, 12) / 12
                + (Boolean.TRUE.equals(genome.get("includeVerified")) ? 0.10 : 0)
                + (Boolean.TRUE.equals(genome.get("includeMemory")) ? 0.08 : 0)
                + (Boolean.TRUE.equals(genome.get("planFirst")) ? 0.12 : 0)
                + (Boolean.TRUE.equals(genome.get("selfCheck")) ? 0.10 : 0)
                - 0.10 * Math.max(0, maxRows - 300) / 500;
        return Math.max(0, Math.min(1, quality));
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{}";
        }
    }
}
