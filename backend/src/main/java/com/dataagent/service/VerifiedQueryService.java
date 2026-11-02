package com.dataagent.service;

import com.dataagent.domain.SemanticEntity;
import com.dataagent.domain.SemanticMetric;
import com.dataagent.domain.VerifiedQuery;
import com.dataagent.repository.SemanticEntityRepository;
import com.dataagent.repository.SemanticMetricRepository;
import com.dataagent.repository.VerifiedQueryRepository;
import com.dataagent.web.dto.SqlDtos;
import com.dataagent.web.dto.VerifiedQueryDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VerifiedQueryService {

    private static final int MAX_CONTEXT_QUERIES = 5;

    private final VerifiedQueryRepository queries;
    private final SemanticMetricRepository metrics;
    private final SemanticEntityRepository entities;
    private final JdbcQueryService jdbcQueries;
    private final DatasourceService datasources;

    public VerifiedQueryService(VerifiedQueryRepository queries,
                                SemanticMetricRepository metrics,
                                SemanticEntityRepository entities,
                                JdbcQueryService jdbcQueries,
                                DatasourceService datasources) {
        this.queries = queries;
        this.metrics = metrics;
        this.entities = entities;
        this.jdbcQueries = jdbcQueries;
        this.datasources = datasources;
    }

    @Transactional(readOnly = true)
    public List<VerifiedQueryDtos.View> list(String q) {
        return (blank(q) ? queries.findAllByOrderByUpdatedAtDesc() : queries.search(q.trim()))
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public VerifiedQueryDtos.View get(UUID id) {
        return view(required(id));
    }

    @Transactional
    public VerifiedQueryDtos.View create(VerifiedQueryDtos.Request request) {
        VerifiedQuery item = new VerifiedQuery();
        apply(item, request);
        return view(queries.save(item));
    }

    @Transactional
    public VerifiedQueryDtos.View update(UUID id, VerifiedQueryDtos.Request request) {
        VerifiedQuery item = required(id);
        apply(item, request);
        return view(queries.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        queries.delete(required(id));
    }

    @Transactional
    public VerifiedQueryDtos.View use(UUID id) {
        VerifiedQuery item = required(id);
        item.setUseCount(item.getUseCount() + 1);
        return view(queries.save(item));
    }

    @Transactional
    public VerifiedQueryDtos.ValidateResult validate(UUID id) {
        VerifiedQuery item = required(id);
        UUID datasourceId = item.getDatasourceId();
        if (datasourceId == null) {
            datasourceId = datasources.list().stream().findFirst()
                    .map(ds -> ds.id())
                    .orElseThrow(() -> new IllegalArgumentException("还没有可用数据源"));
            item.setDatasourceId(datasourceId);
        }
        try {
            SqlDtos.QueryResult result = jdbcQueries.execute(datasourceId, item.getSqlText(), 100, null,
                    false, false, "verified-query", item.getQuestion(), item.getTags());
            item.setLastValidatedAt(Instant.now());
            item.setLastValidationStatus(result.status());
            item.setLastDurationMs(result.elapsedMs());
            item.setLastError(result.status().equals("SUCCESS") ? null : result.message());
            if (result.status().equals("SUCCESS")) {
                item.setExpectedColumns(result.columns().stream()
                        .map(SqlDtos.ColumnMeta::name)
                        .reduce((left, right) -> left + "," + right)
                        .orElse(item.getExpectedColumns()));
            }
            queries.save(item);
            return new VerifiedQueryDtos.ValidateResult(view(item), "SUCCESS".equals(result.status()),
                    result.rowCount(), result.elapsedMs(), result.message());
        } catch (RuntimeException error) {
            item.setLastValidatedAt(Instant.now());
            item.setLastValidationStatus("ERROR");
            item.setLastError(error.getMessage());
            queries.save(item);
            return new VerifiedQueryDtos.ValidateResult(view(item), false, 0, 0, error.getMessage());
        }
    }

    /**
     * Retrieval for the agent context: exact metric/entity links first, then
     * tokenized question/SQL/tag matches. Keeps the prompt small and deterministic.
     */
    @Transactional(readOnly = true)
    public List<VerifiedQueryDtos.View> context(String query, Set<UUID> metricIds, Set<UUID> entityIds) {
        Set<VerifiedQuery> found = new LinkedHashSet<>();
        for (UUID metricId : metricIds) {
            found.addAll(queries.findByMetricOrEntity(metricId, null));
        }
        for (UUID entityId : entityIds) {
            found.addAll(queries.findByMetricOrEntity(null, entityId));
        }
        if (!blank(query)) {
            for (String token : tokenize(query)) {
                found.addAll(queries.search(token));
            }
        }
        return found.stream()
                .sorted((left, right) -> Integer.compare(right.getUseCount(), left.getUseCount()))
                .limit(MAX_CONTEXT_QUERIES)
                .map(this::view)
                .toList();
    }

    private void apply(VerifiedQuery item, VerifiedQueryDtos.Request request) {
        item.setQuestion(request.question().trim());
        item.setSqlText(request.sqlText().trim());
        item.setDatasourceId(request.datasourceId());
        item.setMetricId(request.metricId());
        item.setEntityId(request.entityId());
        item.setTags(trim(request.tags()));
        item.setStatus(blank(request.status()) ? "VERIFIED" : request.status().trim().toUpperCase(Locale.ROOT));
        item.setOwner(trim(request.owner()));
        item.setNotes(trim(request.notes()));
        item.setExpectedColumns(trim(request.expectedColumns()));
        if (item.getMetricId() != null) {
            metrics.findById(item.getMetricId())
                    .orElseThrow(() -> new IllegalArgumentException("指标不存在: " + item.getMetricId()));
        }
        if (item.getEntityId() != null) {
            entities.findById(item.getEntityId())
                    .orElseThrow(() -> new IllegalArgumentException("实体不存在: " + item.getEntityId()));
        }
        if (item.getDatasourceId() != null) {
            datasources.getRequired(item.getDatasourceId());
        }
    }

    private VerifiedQuery required(UUID id) {
        return queries.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("已验证查询不存在: " + id));
    }

    private VerifiedQueryDtos.View view(VerifiedQuery item) {
        return new VerifiedQueryDtos.View(item.getId(), item.getQuestion(), item.getSqlText(),
                item.getDatasourceId(), item.getMetricId(), item.getEntityId(), item.getTags(),
                item.getStatus(), item.getOwner(), item.getNotes(), item.getExpectedColumns(),
                item.getUseCount(), item.getLastValidatedAt(), item.getLastValidationStatus(),
                item.getLastDurationMs(), item.getLastError(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        for (String token : query.replaceAll("[\\p{Punct}，。；：！？、]+", " ").trim().split("\\s+")) {
            if (!token.isBlank() && token.length() >= 2) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
