package com.dataagent.service;

import com.dataagent.domain.Datasource;
import com.dataagent.domain.KnowledgeEntry;
import com.dataagent.domain.QueryExecution;
import com.dataagent.repository.DatasourceRepository;
import com.dataagent.repository.KnowledgeEntryRepository;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.HistoryDtos;
import com.dataagent.web.dto.KnowledgeDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private static final long SLOW_THRESHOLD_MS = 1_000;

    private final QueryExecutionRepository executions;
    private final DatasourceRepository datasources;
    private final KnowledgeEntryRepository knowledge;
    private final KnowledgeService knowledgeService;

    public HistoryService(QueryExecutionRepository executions, DatasourceRepository datasources,
                          KnowledgeEntryRepository knowledge, KnowledgeService knowledgeService) {
        this.executions = executions;
        this.datasources = datasources;
        this.knowledge = knowledge;
        this.knowledgeService = knowledgeService;
    }

    @Transactional(readOnly = true)
    public HistoryDtos.HistoryPage search(UUID datasourceId, Boolean solved, String query, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<QueryExecution> result = executions.search(
                datasourceId, solved, query == null || query.isBlank() ? null : query.trim(), pageable);
        Map<UUID, Datasource> datasourceMap = datasourceMap();
        List<HistoryDtos.HistoryView> items = result.getContent().stream()
                .map(execution -> toView(execution, datasourceMap.get(execution.getDatasourceId()), false))
                .toList();
        return new HistoryDtos.HistoryPage(items, result.getTotalElements(), result.getNumber(),
                result.getSize(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public HistoryDtos.HistoryDetail detail(UUID id) {
        QueryExecution execution = getRequired(id);
        Datasource datasource = datasources.findById(execution.getDatasourceId()).orElse(null);
        List<KnowledgeDtos.KnowledgeView> related = new ArrayList<>();
        if (execution.getKnowledgeId() != null) {
            knowledge.findById(execution.getKnowledgeId())
                    .map(knowledgeService::viewOf)
                    .ifPresent(related::add);
        } else {
            knowledge.findAll().stream()
                    .filter(entry -> execution.getId().equals(entry.getSourceQueryId()))
                    .findFirst()
                    .map(knowledgeService::viewOf)
                    .ifPresent(related::add);
        }
        return new HistoryDtos.HistoryDetail(toView(execution, datasource, true), related);
    }

    @Transactional
    public HistoryDtos.SolveResult markSolved(UUID id, HistoryDtos.SolveRequest request, String user) {
        QueryExecution execution = getRequired(id);
        Datasource datasource = datasources.findById(execution.getDatasourceId()).orElse(null);
        KnowledgeEntry entry = execution.getKnowledgeId() == null
                ? null
                : knowledge.findById(execution.getKnowledgeId()).orElse(null);
        if (entry == null) {
            entry = new KnowledgeEntry();
        }

        String problem = firstNonBlank(request.problem(), execution.getProblem(), "SQL 执行耗时/计划优化");
        String title = firstNonBlank(request.title(), titleFrom(problem, execution.getSqlText()));
        entry.setTitle(truncate(title, 300));
        entry.setProblem(problem);
        entry.setSqlText(execution.getSqlText());
        entry.setOptimizedSql(blankToNull(request.optimizedSql()));
        entry.setDialect(datasource == null ? null : datasource.getDialect());
        entry.setTags(mergeTags(execution.getTags(), request.tags()));
        entry.setPlanSummary(planSummary(execution));
        entry.setRootCause(blankToNull(request.rootCause()));
        entry.setOptimizationSteps(blankToNull(request.optimizationSteps()));
        entry.setBeforeDurationMs(execution.getDurationMs() > 0 ? execution.getDurationMs() : null);
        entry.setAfterDurationMs(request.afterDurationMs());
        entry.setAuthor(user);
        entry.setSourceQueryId(execution.getId());
        knowledge.save(entry);

        execution.setProblem(problem);
        if (request.tags() != null) {
            execution.setTags(mergeTags(execution.getTags(), request.tags()));
        }
        execution.setSolved(true);
        execution.setKnowledgeId(entry.getId());
        executions.save(execution);

        return new HistoryDtos.SolveResult(execution.getId(), entry.getId(), knowledgeService.viewOf(entry));
    }

    @Transactional
    public HistoryDtos.HistoryView updateNote(UUID id, HistoryDtos.NoteRequest request) {
        QueryExecution execution = getRequired(id);
        if (request.problem() != null) {
            execution.setProblem(blankToNull(request.problem()));
        }
        if (request.tags() != null) {
            execution.setTags(mergeTags(execution.getTags(), request.tags()));
        }
        executions.save(execution);
        Datasource datasource = datasources.findById(execution.getDatasourceId()).orElse(null);
        return toView(execution, datasource, true);
    }

    @Transactional(readOnly = true)
    public HistoryDtos.QueryStats stats() {
        Instant today = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Long> durations = executions.allSuccessfulDurations();
        long p95 = percentile(durations, 0.95);
        Map<UUID, Datasource> datasourceMap = datasourceMap();
        List<HistoryDtos.HistoryView> recent = executions.findTop20ByOrderByCreatedAtDesc().stream()
                .limit(8)
                .map(execution -> toView(execution, datasourceMap.get(execution.getDatasourceId()), false))
                .toList();
        List<HistoryDtos.HistoryView> slowest = executions.findTop10ByOrderByDurationMsDesc().stream()
                .limit(8)
                .map(execution -> toView(execution, datasourceMap.get(execution.getDatasourceId()), false))
                .toList();
        Double average = executions.averageDuration();
        return new HistoryDtos.QueryStats(
                executions.count(),
                executions.countByCreatedAtAfter(today),
                average == null ? 0 : Math.round(average * 100.0) / 100.0,
                p95,
                executions.countByDurationMsGreaterThanEqual(SLOW_THRESHOLD_MS),
                executions.countBySolvedTrue(),
                knowledge.count(),
                datasources.count(),
                recent,
                slowest);
    }

    @Transactional(readOnly = true)
    public QueryExecution getRequired(UUID id) {
        return executions.findById(id)
                .orElseThrow(() -> ApiException.notFound("执行记录不存在: " + id));
    }

    private HistoryDtos.HistoryView toView(QueryExecution execution, Datasource datasource, boolean includePlan) {
        return new HistoryDtos.HistoryView(
                execution.getId(),
                execution.getDatasourceId(),
                datasource == null ? null : datasource.getName(),
                datasource == null ? null : datasource.getDialect(),
                execution.getUserId(),
                execution.getKind(),
                execution.getSqlText(),
                execution.getStatus(),
                execution.getDurationMs(),
                execution.getFetchMs(),
                execution.getRowCount(),
                execution.isTruncated(),
                execution.getErrorMessage(),
                includePlan ? execution.getPlanRaw() : null,
                includePlan ? execution.getPlanFormat() : null,
                execution.getProblem(),
                execution.getTags(),
                execution.isSolved(),
                execution.getKnowledgeId(),
                execution.getCreatedAt());
    }

    private Map<UUID, Datasource> datasourceMap() {
        return datasources.findAll().stream()
                .collect(Collectors.toMap(Datasource::getId, Function.identity()));
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        List<Long> values = sortedValues.stream().sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private String planSummary(QueryExecution execution) {
        if (execution.getPlanRaw() == null || execution.getPlanRaw().isBlank()) {
            return null;
        }
        String raw = execution.getPlanRaw().trim();
        return raw.length() > 2000 ? raw.substring(0, 2000) + "…" : raw;
    }

    private String mergeTags(String existing, String incoming) {
        Set<String> tags = new LinkedHashSet<>();
        for (String source : new String[]{existing, incoming}) {
            if (source == null || source.isBlank()) {
                continue;
            }
            for (String part : source.split("[,，]")) {
                String value = part.trim();
                if (!value.isBlank()) {
                    tags.add(value.length() > 40 ? value.substring(0, 40) : value);
                }
            }
        }
        String joined = String.join(",", tags);
        return joined.isBlank() ? null : (joined.length() > 500 ? joined.substring(0, 500) : joined);
    }

    private String titleFrom(String problem, String sql) {
        String base = problem == null || problem.isBlank() ? "SQL: " + sql : problem;
        return truncate(base.replaceAll("\\s+", " ").trim(), 120);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
