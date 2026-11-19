package com.dataagent.service;

import com.dataagent.domain.Datasource;
import com.dataagent.domain.KnowledgeAccumulationRun;
import com.dataagent.domain.KnowledgeEntry;
import com.dataagent.domain.QueryExecution;
import com.dataagent.repository.DatasourceRepository;
import com.dataagent.repository.KnowledgeAccumulationRunRepository;
import com.dataagent.repository.KnowledgeEntryRepository;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.dto.KnowledgeDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Long-term knowledge accumulation. Periodically promotes the SQL patterns that
 * keep recurring (same fingerprint executed at least {@code minFrequency} times)
 * into the knowledge base, then merges duplicate entries so the base stays compact.
 */
@Service
public class KnowledgeAccumulationService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAccumulationService.class);

    private final QueryExecutionRepository executions;
    private final KnowledgeEntryRepository knowledge;
    private final KnowledgeAccumulationRunRepository runs;
    private final DatasourceRepository datasources;
    private final SqlAssetService sqlAssets;
    private final KnowledgeExtractionService extractor;

    public KnowledgeAccumulationService(QueryExecutionRepository executions,
                                        KnowledgeEntryRepository knowledge,
                                        KnowledgeAccumulationRunRepository runs,
                                        DatasourceRepository datasources,
                                        SqlAssetService sqlAssets,
                                        KnowledgeExtractionService extractor) {
        this.executions = executions;
        this.knowledge = knowledge;
        this.runs = runs;
        this.datasources = datasources;
        this.sqlAssets = sqlAssets;
        this.extractor = extractor;
    }

    @Transactional
    public KnowledgeDtos.AccumulationResult accumulate(String trigger, Integer minFrequency, String user) {
        int threshold = minFrequency == null ? 3 : Math.max(1, Math.min(minFrequency, 1000));
        Instant startedAt = Instant.now();
        int promoted = promoteFrequent(threshold, user);
        int[] mergedAndDeduped = deduplicate();
        int merged = mergedAndDeduped[0];
        int deduped = mergedAndDeduped[1];

        KnowledgeAccumulationRun run = new KnowledgeAccumulationRun();
        run.setTenantId(AuthContext.currentOrSystem().tenantId());
        run.setTriggerType(trigger == null || trigger.isBlank() ? "MANUAL" : trigger);
        run.setMinFrequency(threshold);
        run.setPromoted(promoted);
        run.setMerged(merged);
        run.setDeduped(deduped);
        run.setTotalKnowledge((int) knowledge.count());
        run.setCreatedAt(startedAt);
        runs.save(run);

        log.info("Knowledge accumulation ({}): promoted {} patterns, merged {} groups, deduped {} entries, total {}",
                run.getTriggerType(), promoted, merged, deduped, run.getTotalKnowledge());
        return new KnowledgeDtos.AccumulationResult(run.getId(), run.getTriggerType(), threshold,
                promoted, merged, deduped, run.getTotalKnowledge(), startedAt);
    }

    private int promoteFrequent(int threshold, String user) {
        List<Object[]> frequent = executions.frequentFingerprints(threshold);
        int promoted = 0;
        for (Object[] row : frequent) {
            String fingerprint = (String) row[0];
            List<QueryExecution> matches = executions.findByFingerprintOrderByCreatedAtDesc(fingerprint);
            if (matches.isEmpty()) {
                continue;
            }
            QueryExecution canonical = matches.get(0);
            if (canonical.isSolved()) {
                continue;
            }
            KnowledgeEntry entry = new KnowledgeEntry();
            String dialect = dialectOf(canonical.getDatasourceId());
            KnowledgeExtractionService.ExtractionResult extracted = extractor.extract(
                    canonical.getSqlText(), canonical.getProblem(), canonical.getTags(), dialect);
            entry.setTitle(extracted.title());
            entry.setProblem(blankToNull(canonical.getProblem()));
            entry.setSqlText(canonical.getSqlText());
            entry.setDialect(extracted.dialect());
            entry.setTags(mergeTags(extracted.tagsCsv(), "自动累积"));
            entry.setBeforeDurationMs(canonical.getDurationMs() > 0 ? canonical.getDurationMs() : null);
            entry.setAuthor(user);
            entry.setSourceQueryId(canonical.getId());
            knowledge.save(entry);

            for (QueryExecution match : matches) {
                match.setSolved(true);
                match.setKnowledgeId(entry.getId());
            }
            executions.saveAll(matches);
            sqlAssets.linkKnowledge(canonical, entry);
            promoted++;
        }
        return promoted;
    }

    /**
     * Merges knowledge entries whose SQL is identical after normalization.
     * Returns {@code [mergedGroups, dedupedEntries]}.
     */
    private int[] deduplicate() {
        List<KnowledgeEntry> all = knowledge.findAll();
        Map<String, List<KnowledgeEntry>> bySql = new HashMap<>();
        for (KnowledgeEntry entry : all) {
            bySql.computeIfAbsent(normalize(entry.getSqlText()), k -> new java.util.ArrayList<>())
                    .add(entry);
        }
        int merged = 0;
        int deduped = 0;
        for (List<KnowledgeEntry> group : bySql.values()) {
            if (group.size() <= 1) {
                continue;
            }
            group.sort(Comparator
                    .comparingInt(KnowledgeEntry::getUsageCount).reversed()
                    .thenComparing(KnowledgeEntry::getUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())));
            KnowledgeEntry survivor = group.get(0);
            int totalUsage = survivor.getUsageCount();
            for (int i = 1; i < group.size(); i++) {
                KnowledgeEntry duplicate = group.get(i);
                totalUsage += duplicate.getUsageCount();
                knowledge.delete(duplicate);
                deduped++;
            }
            if (totalUsage != survivor.getUsageCount()) {
                survivor.setUsageCount(totalUsage);
                knowledge.save(survivor);
                merged++;
            }
        }
        return new int[]{merged, deduped};
    }

    private String titleFrom(QueryExecution execution) {
        String base = execution.getProblem() == null || execution.getProblem().isBlank()
                ? "SQL: " + execution.getSqlText()
                : execution.getProblem();
        String value = base.replaceAll("\\s+", " ").trim();
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }

    private String dialectOf(UUID datasourceId) {
        return datasources.findById(datasourceId).map(Datasource::getDialect).orElse(null);
    }

    private String normalize(String sql) {
        return sql == null ? "" : sql.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String mergeTags(String existing, String extra) {
        String base = blankToNull(existing);
        if (base == null) {
            return extra;
        }
        if (base.contains(extra)) {
            return base;
        }
        String joined = base + "," + extra;
        return joined.length() <= 500 ? joined : joined.substring(0, 500);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
