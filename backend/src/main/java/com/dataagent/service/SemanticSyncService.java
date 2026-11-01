package com.dataagent.service;

import com.dataagent.domain.KnowledgeEntry;
import com.dataagent.domain.SemanticEntity;
import com.dataagent.domain.SemanticLink;
import com.dataagent.domain.SemanticMetric;
import com.dataagent.domain.SemanticSyncRun;
import com.dataagent.domain.SemanticTerm;
import com.dataagent.domain.SqlAsset;
import com.dataagent.repository.KnowledgeEntryRepository;
import com.dataagent.repository.SemanticEntityRepository;
import com.dataagent.repository.SemanticLinkRepository;
import com.dataagent.repository.SemanticMetricRepository;
import com.dataagent.repository.SemanticSyncRunRepository;
import com.dataagent.repository.SemanticTermRepository;
import com.dataagent.repository.SqlAssetRepository;
import com.dataagent.web.dto.SemanticDtos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SemanticSyncService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSyncService.class);
    private static final Set<String> STOP_ALIASES = Set.of(
            "sql", "select", "from", "where", "table", "查询", "数据", "报表", "字段", "系统", "平台");
    private static final int MAX_LINKS_PER_SOURCE = 12;

    private final SemanticEntityRepository entities;
    private final SemanticMetricRepository metrics;
    private final SemanticTermRepository terms;
    private final SemanticLinkRepository links;
    private final SemanticSyncRunRepository runs;
    private final KnowledgeEntryRepository knowledge;
    private final SqlAssetRepository assets;
    private final MeterRegistry meterRegistry;

    public SemanticSyncService(SemanticEntityRepository entities, SemanticMetricRepository metrics,
                               SemanticTermRepository terms, SemanticLinkRepository links,
                               SemanticSyncRunRepository runs, KnowledgeEntryRepository knowledge,
                               SqlAssetRepository assets, MeterRegistry meterRegistry) {
        this.entities = entities;
        this.metrics = metrics;
        this.terms = terms;
        this.links = links;
        this.runs = runs;
        this.knowledge = knowledge;
        this.assets = assets;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Reconciles AUTO links between the semantic layer and the two knowledge sources:
     * curated knowledge entries and cached SQL assets. MANUAL links are never touched.
     */
    public SemanticDtos.SyncRunView sync(String triggerType) {
        long startedAt = System.currentTimeMillis();
        SemanticSyncRun run = new SemanticSyncRun();
        run.setTriggerType(triggerType == null ? "MANUAL" : triggerType);
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        runs.save(run);

        try {
            List<Target> targets = loadTargets();
            List<KnowledgeEntry> knowledgeEntries = knowledge.findAll();
            List<SqlAsset> sqlAssets = assets.findAll();
            List<SemanticLink> existingAuto = links.findByLinkSource("AUTO");
            Map<String, SemanticLink> existingByKey = new HashMap<>();
            for (SemanticLink link : existingAuto) {
                existingByKey.put(key(link.getSourceType(), link.getSourceId(), link.getTargetType(), link.getTargetId()), link);
            }

            Set<String> seen = new HashSet<>();
            int created = 0;
            int updated = 0;
            Set<UUID> linkedAssetIds = new HashSet<>();

            for (KnowledgeEntry entry : knowledgeEntries) {
                String text = knowledgeText(entry);
                List<Match> matches = matchTargets(text, targets);
                for (Match match : matches) {
                    String key = key("KNOWLEDGE", entry.getId(), match.target.type(), match.target.id());
                    seen.add(key);
                    if (applyLink(existingByKey.get(key), "KNOWLEDGE", entry.getId(), match)) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            }

            for (SqlAsset asset : sqlAssets) {
                String text = assetText(asset);
                List<Match> matches = matchTargets(text, targets);
                for (Match match : matches) {
                    String key = key("SQL_ASSET", asset.getId(), match.target.type(), match.target.id());
                    seen.add(key);
                    if (applyLink(existingByKey.get(key), "SQL_ASSET", asset.getId(), match)) {
                        created++;
                    } else {
                        updated++;
                    }
                    linkedAssetIds.add(asset.getId());
                }
            }

            List<SemanticLink> stale = existingAuto.stream()
                    .filter(link -> !seen.contains(key(link.getSourceType(), link.getSourceId(),
                            link.getTargetType(), link.getTargetId())))
                    .toList();
            if (!stale.isEmpty()) {
                links.deleteAll(stale);
            }

            Instant syncedAt = Instant.now();
            for (SqlAsset asset : sqlAssets) {
                if (linkedAssetIds.contains(asset.getId())) {
                    asset.setSemanticSyncedAt(syncedAt);
                    assets.save(asset);
                }
            }

            run.setStatus("SUCCESS");
            run.setFinishedAt(syncedAt);
            run.setScannedAssets(sqlAssets.size());
            run.setScannedKnowledge(knowledgeEntries.size());
            run.setCreatedLinks(created);
            run.setUpdatedLinks(updated);
            run.setRemovedLinks(stale.size());
            run.setMessage("语义层与知识库同步完成");
            runs.save(run);
            log.info("Semantic sync {}: assets={}, knowledge={}, created={}, updated={}, removed={}",
                    run.getId(), sqlAssets.size(), knowledgeEntries.size(), created, updated, stale.size());
            recordSyncMetrics(startedAt, "SUCCESS", created, updated, stale.size());
            return toView(run);
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setFinishedAt(Instant.now());
            run.setMessage(e.getMessage());
            runs.save(run);
            log.error("Semantic sync failed: {}", e.getMessage(), e);
            recordSyncMetrics(startedAt, "FAILED", 0, 0, 0);
            return toView(run);
        }
    }

    private void recordSyncMetrics(long startedAt, String status, int created, int updated, int removed) {
        try {
            long elapsed = Math.max(0, System.currentTimeMillis() - startedAt);
            Timer.builder("dataagent.semantic.sync.duration")
                    .tag("status", status)
                    .register(meterRegistry)
                    .record(Duration.ofMillis(elapsed));
            Counter.builder("dataagent.semantic.sync.links.created").register(meterRegistry).increment(created);
            Counter.builder("dataagent.semantic.sync.links.updated").register(meterRegistry).increment(updated);
            Counter.builder("dataagent.semantic.sync.links.removed").register(meterRegistry).increment(removed);
        } catch (Exception metricError) {
            log.debug("Failed to record semantic sync metrics", metricError);
        }
    }

    private List<Target> loadTargets() {
        List<Target> targets = new ArrayList<>();
        for (SemanticEntity entity : entities.findAll()) {
            targets.add(new Target("ENTITY", entity.getId(), entity.getDisplayName(),
                    aliases(entity.getDisplayName(), entity.getName(), entity.getSynonyms(), entity.getTags())));
        }
        for (SemanticMetric metric : metrics.findAll()) {
            targets.add(new Target("METRIC", metric.getId(), metric.getDisplayName(),
                    aliases(metric.getDisplayName(), metric.getName(), metric.getSynonyms(), metric.getTags())));
        }
        for (SemanticTerm term : terms.findAll()) {
            targets.add(new Target("TERM", term.getId(), term.getTerm(),
                    aliases(term.getTerm(), term.getTerm(), term.getSynonyms(), term.getTags())));
        }
        return targets;
    }

    private List<Match> matchTargets(String text, List<Target> targets) {
        List<Match> matches = new ArrayList<>();
        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (Target target : targets) {
            Match best = null;
            for (String alias : target.aliases()) {
                if (alias.length() < 2 || STOP_ALIASES.contains(alias)) {
                    continue;
                }
                if (alias.length() <= 2 && alias.chars().allMatch(c -> c < 128)) {
                    continue;
                }
                if (!normalizedText.contains(alias)) {
                    continue;
                }
                int score = alias.equals(target.displayName().toLowerCase(Locale.ROOT)) ? 95
                        : alias.length() >= 4 ? 78 : 66;
                if (best == null || score > best.confidence()) {
                    best = new Match(target, score, "命中「" + target.displayName() + "」的别名：" + alias);
                }
            }
            if (best != null) {
                matches.add(best);
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(Match::confidence).reversed())
                .limit(MAX_LINKS_PER_SOURCE)
                .toList();
    }

    private boolean applyLink(SemanticLink existing, String sourceType, UUID sourceId, Match match) {
        boolean created = existing == null;
        SemanticLink link = existing == null ? new SemanticLink() : existing;
        link.setSourceType(sourceType);
        link.setSourceId(sourceId);
        link.setTargetType(match.target.type());
        link.setTargetId(match.target.id());
        link.setConfidence(match.confidence());
        link.setReason(match.reason());
        link.setLinkSource("AUTO");
        links.save(link);
        return created;
    }

    private String knowledgeText(KnowledgeEntry entry) {
        return String.join("\n",
                nullToEmpty(entry.getTitle()),
                nullToEmpty(entry.getProblem()),
                nullToEmpty(entry.getSqlText()),
                nullToEmpty(entry.getTags()),
                nullToEmpty(entry.getRootCause()),
                nullToEmpty(entry.getOptimizationSteps())).toLowerCase(Locale.ROOT);
    }

    private String assetText(SqlAsset asset) {
        return String.join("\n",
                nullToEmpty(asset.getBusinessProblem()),
                nullToEmpty(asset.getSqlText()),
                nullToEmpty(asset.getTags()),
                nullToEmpty(asset.getDomain())).toLowerCase(Locale.ROOT);
    }

    private Set<String> aliases(String displayName, String name, String synonyms, String tags) {
        Set<String> values = new LinkedHashSet<>();
        for (String source : new String[]{displayName, name, synonyms}) {
            if (source == null || source.isBlank()) {
                continue;
            }
            for (String part : source.split("[,，/;；|]")) {
                String alias = part.trim().toLowerCase(Locale.ROOT);
                if (!alias.isBlank()) {
                    values.add(alias);
                }
            }
        }
        return values;
    }

    private String key(String sourceType, UUID sourceId, String targetType, UUID targetId) {
        return sourceType + "|" + sourceId + "|" + targetType + "|" + targetId;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private SemanticDtos.SyncRunView toView(SemanticSyncRun run) {
        return new SemanticDtos.SyncRunView(
                run.getId(), run.getTriggerType(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
                run.getScannedAssets(), run.getScannedKnowledge(), run.getCreatedLinks(),
                run.getUpdatedLinks(), run.getRemovedLinks(), run.getMessage());
    }

    private record Target(String type, UUID id, String displayName, Set<String> aliases) {
    }

    private record Match(Target target, int confidence, String reason) {
    }
}
