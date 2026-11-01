package com.dataagent.service;

import com.dataagent.domain.Datasource;
import com.dataagent.domain.KnowledgeEntry;
import com.dataagent.domain.QueryExecution;
import com.dataagent.domain.SqlAsset;
import com.dataagent.repository.DatasourceRepository;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.repository.SqlAssetRepository;
import com.dataagent.sql.SqlGuard;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.SqlAssetDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SqlAssetService {

    private final SqlAssetRepository assets;
    private final QueryExecutionRepository executions;
    private final DatasourceRepository datasources;
    private final SqlGuard guard;

    public SqlAssetService(SqlAssetRepository assets, QueryExecutionRepository executions,
                           DatasourceRepository datasources, SqlGuard guard) {
        this.assets = assets;
        this.executions = executions;
        this.datasources = datasources;
        this.guard = guard;
    }

    /**
     * Every executed read-only SQL becomes part of the development knowledge cache.
     * The fingerprint intentionally normalizes literals so similar investigations group together.
     */
    @Transactional
    public void cacheExecution(QueryExecution execution, Datasource datasource) {
        if (execution == null || execution.getSqlText() == null || execution.getSqlText().isBlank()) {
            return;
        }
        if (!"QUERY".equalsIgnoreCase(execution.getKind())) {
            return;
        }
        String fingerprint = guard.fingerprint(execution.getSqlText());
        SqlAsset asset = assets.findByFingerprint(fingerprint).orElseGet(SqlAsset::new);
        boolean created = asset.getId() == null;
        Instant seen = execution.getCreatedAt() == null ? Instant.now() : execution.getCreatedAt();

        asset.setFingerprint(fingerprint);
        asset.setSqlText(execution.getSqlText());
        asset.setDatasourceId(execution.getDatasourceId());
        if (created || asset.getSource() == null || asset.getSource().isBlank()) {
            asset.setSource("EXECUTION");
        }
        if (execution.getProblem() != null && !execution.getProblem().isBlank()) {
            asset.setBusinessProblem(truncate(execution.getProblem(), 8000));
        }
        asset.setTags(mergeTags(asset.getTags(), execution.getTags()));
        if (execution.getUserId() != null && (asset.getAuthor() == null || asset.getAuthor().isBlank())) {
            asset.setAuthor(execution.getUserId());
        }
        if (created || asset.getFirstSeen() == null) {
            asset.setFirstSeen(seen);
        }
        asset.setLastSeen(seen);
        asset.setExecutionCount(asset.getExecutionCount() + 1);
        if ("SUCCESS".equalsIgnoreCase(execution.getStatus())) {
            asset.setSuccessCount(asset.getSuccessCount() + 1);
        } else {
            asset.setFailureCount(asset.getFailureCount() + 1);
        }
        int previousCount = Math.max(0, asset.getExecutionCount() - 1);
        long previousAvg = asset.getAvgDurationMs() == null ? 0 : asset.getAvgDurationMs();
        long newAvg = previousCount == 0
                ? execution.getDurationMs()
                : Math.round((previousAvg * (double) previousCount + execution.getDurationMs())
                        / asset.getExecutionCount());
        asset.setAvgDurationMs(newAvg);
        asset.setLastDurationMs(execution.getDurationMs());
        if (execution.getPlanRaw() != null && !execution.getPlanRaw().isBlank()) {
            asset.setLastPlan(truncate(execution.getPlanRaw(), 100_000));
            asset.setLastPlanFormat(execution.getPlanFormat());
        }
        assets.save(asset);
    }

    @Transactional
    public SqlAssetDtos.SqlAssetView saveDraft(SqlAssetDtos.SaveRequest request, String user) {
        String sql = request.sql() == null ? "" : request.sql().trim();
        if (sql.isBlank()) {
            throw ApiException.badRequest("SQL 不能为空");
        }
        String fingerprint = guard.fingerprint(sql);
        SqlAsset asset = assets.findByFingerprint(fingerprint).orElseGet(SqlAsset::new);
        boolean created = asset.getId() == null;
        asset.setFingerprint(fingerprint);
        asset.setSqlText(sql);
        if (request.businessProblem() != null && !request.businessProblem().isBlank()) {
            asset.setBusinessProblem(truncate(request.businessProblem(), 8000));
        }
        if (request.domain() != null && !request.domain().isBlank()) {
            asset.setDomain(truncate(request.domain(), 120));
        }
        asset.setTags(mergeTags(asset.getTags(), request.tags()));
        if (request.datasourceId() != null) {
            asset.setDatasourceId(request.datasourceId());
        }
        if (request.author() != null && !request.author().isBlank()) {
            asset.setAuthor(truncate(request.author(), 120));
        } else if (asset.getAuthor() == null || asset.getAuthor().isBlank()) {
            asset.setAuthor(user);
        }
        if (created || asset.getSource() == null || asset.getSource().isBlank()) {
            asset.setSource("EDITOR");
        }
        Instant now = Instant.now();
        if (created || asset.getFirstSeen() == null) {
            asset.setFirstSeen(now);
        }
        asset.setLastSeen(now);
        assets.save(asset);
        return toView(asset);
    }

    @Transactional
    public int importAssets(SqlAssetDtos.ImportRequest request, String user) {
        int count = 0;
        for (SqlAssetDtos.SaveRequest item : request.items()) {
            if (item != null && item.sql() != null && !item.sql().isBlank()) {
                saveDraft(item, user);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public void linkKnowledge(QueryExecution execution, KnowledgeEntry knowledge) {
        if (execution == null || knowledge == null || execution.getSqlText() == null) {
            return;
        }
        String fingerprint = guard.fingerprint(execution.getSqlText());
        SqlAsset asset = assets.findByFingerprint(fingerprint).orElseGet(SqlAsset::new);
        if (asset.getId() == null) {
            asset.setFingerprint(fingerprint);
            asset.setSource("KNOWLEDGE");
            asset.setFirstSeen(execution.getCreatedAt() == null ? Instant.now() : execution.getCreatedAt());
        }
        asset.setSqlText(execution.getSqlText());
        asset.setDatasourceId(execution.getDatasourceId());
        asset.setKnowledgeId(knowledge.getId());
        if (knowledge.getProblem() != null && !knowledge.getProblem().isBlank()) {
            asset.setBusinessProblem(truncate(knowledge.getProblem(), 8000));
        }
        asset.setTags(mergeTags(asset.getTags(), knowledge.getTags()));
        asset.setLastSeen(Instant.now());
        if (asset.getExecutionCount() == 0 && execution.getDurationMs() >= 0) {
            asset.setExecutionCount(1);
            if ("SUCCESS".equalsIgnoreCase(execution.getStatus())) {
                asset.setSuccessCount(1);
            } else {
                asset.setFailureCount(1);
            }
            asset.setAvgDurationMs(execution.getDurationMs());
            asset.setLastDurationMs(execution.getDurationMs());
        }
        assets.save(asset);
    }

    @Transactional(readOnly = true)
    public SqlAssetDtos.AssetPage search(String query, String domain, UUID datasourceId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<SqlAsset> result = assets.search(datasourceId, blankToNull(domain), blankToNull(query), pageable);
        return new SqlAssetDtos.AssetPage(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SqlAssetDtos.SqlAssetView get(UUID id) {
        return toView(getRequired(id));
    }

    @Transactional(readOnly = true)
    public SqlAsset getRequired(UUID id) {
        return assets.findById(id).orElseThrow(() -> ApiException.notFound("SQL 资产不存在: " + id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!assets.existsById(id)) {
            throw ApiException.notFound("SQL 资产不存在: " + id);
        }
        assets.deleteById(id);
    }

    @Transactional(readOnly = true)
    public SqlAssetDtos.CacheStats stats() {
        List<SqlAsset> all = assets.findAll();
        long withProblem = all.stream()
                .filter(asset -> asset.getBusinessProblem() != null && !asset.getBusinessProblem().isBlank())
                .count();
        long withKnowledge = all.stream().filter(asset -> asset.getKnowledgeId() != null).count();
        long linked = all.stream().filter(asset -> asset.getSemanticSyncedAt() != null).count();
        return new SqlAssetDtos.CacheStats(all.size(), withProblem, withKnowledge, linked);
    }

    /**
     * One-time-ish backfill for installations that already had query history before V2.
     */
    @Transactional
    public int backfillFromExecutions() {
        int cached = 0;
        for (QueryExecution execution : executions.findAll()) {
            if (!"QUERY".equalsIgnoreCase(execution.getKind())) {
                continue;
            }
            if (assets.findByFingerprint(guard.fingerprint(execution.getSqlText())).isPresent()) {
                continue;
            }
            Datasource datasource = datasources.findById(execution.getDatasourceId()).orElse(null);
            cacheExecution(execution, datasource);
            cached++;
        }
        return cached;
    }

    public SqlAssetDtos.SqlAssetView toView(SqlAsset asset) {
        return new SqlAssetDtos.SqlAssetView(
                asset.getId(),
                asset.getSqlText(),
                asset.getBusinessProblem(),
                asset.getDomain(),
                asset.getTags(),
                asset.getDatasourceId(),
                asset.getAuthor(),
                asset.getSource(),
                asset.getExecutionCount(),
                asset.getSuccessCount(),
                asset.getFailureCount(),
                asset.getAvgDurationMs(),
                asset.getLastDurationMs(),
                asset.getKnowledgeId(),
                asset.getFirstSeen(),
                asset.getLastSeen(),
                asset.getSemanticSyncedAt(),
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }

    public String mergeTags(String existing, String incoming) {
        Set<String> tags = new LinkedHashSet<>();
        for (String source : new String[]{existing, incoming}) {
            if (source == null || source.isBlank()) {
                continue;
            }
            for (String part : source.split("[,，]")) {
                String value = part.trim();
                if (!value.isBlank()) {
                    tags.add(truncate(value, 40));
                }
            }
        }
        String joined = String.join(",", tags);
        return joined.isBlank() ? null : truncate(joined, 500);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
