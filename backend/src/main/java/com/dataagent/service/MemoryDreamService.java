package com.dataagent.service;

import com.dataagent.domain.AgentMemory;
import com.dataagent.domain.MemoryContextSnapshot;
import com.dataagent.domain.MemoryDreamRun;
import com.dataagent.domain.MemoryWikiLink;
import com.dataagent.domain.MemoryWikiPage;
import com.dataagent.domain.QueryExecution;
import com.dataagent.repository.AgentMemoryRepository;
import com.dataagent.repository.MemoryContextSnapshotRepository;
import com.dataagent.repository.MemoryDreamRunRepository;
import com.dataagent.repository.MemoryWikiLinkRepository;
import com.dataagent.repository.MemoryWikiPageRepository;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.MemoryDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Offline "dreaming" pass over the agent's notes and logs. It follows the
 * hierarchy described by Anthropic: transcripts -> notes -> wiki/representations
 * -> small injected context. Dreaming is allowed to merge notes, correct
 * stale/volatile notes against recent transcripts, rebuild wiki pages and
 * links, and publish a bounded context index.
 */
@Service
public class MemoryDreamService {

    private static final int CONTEXT_BUDGET_TOKENS = 1600;
    private static final int INJECTED_NOTES = 8;
    private static final int WIKI_PAGES = 12;

    private final AgentMemoryRepository memories;
    private final MemoryWikiPageRepository pages;
    private final MemoryWikiLinkRepository links;
    private final MemoryDreamRunRepository dreams;
    private final MemoryContextSnapshotRepository snapshots;
    private final QueryExecutionRepository transcripts;
    private final MemoryService memoryService;
    private final EmbeddingService embeddings;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public MemoryDreamService(AgentMemoryRepository memories, MemoryWikiPageRepository pages,
                              MemoryWikiLinkRepository links, MemoryDreamRunRepository dreams,
                              MemoryContextSnapshotRepository snapshots,
                              QueryExecutionRepository transcripts, MemoryService memoryService,
                              EmbeddingService embeddings, AuditService audit,
                              ObjectMapper objectMapper) {
        this.memories = memories;
        this.pages = pages;
        this.links = links;
        this.dreams = dreams;
        this.snapshots = snapshots;
        this.transcripts = transcripts;
        this.memoryService = memoryService;
        this.embeddings = embeddings;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MemoryDtos.DreamRunView dream(String triggerType) {
        String tenant = tenant();
        MemoryDreamRun run = new MemoryDreamRun();
        run.setTenantId(tenant);
        run.setTriggerType(triggerType == null || triggerType.isBlank() ? "MANUAL" : triggerType);
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run = dreams.save(run);

        List<AgentMemory> active = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant, "ACTIVE");
        List<QueryExecution> recent = transcripts.findTop20ByOrderByCreatedAtDesc();
        run.setScannedMemories(active.size());
        run.setScannedTranscripts(recent.size());

        int merged = consolidate(active);
        int corrected = correctAgainstTranscripts(active, recent);
        active.forEach(this::scoreSalience);
        memories.saveAll(active);

        Map<UUID, MemoryWikiPage> pageByMemory = new LinkedHashMap<>();
        List<AgentMemory> promotable = active.stream()
                .filter(memory -> memory.getSalience() >= 45 && memory.getImportance() >= 35)
                .sorted(Comparator.comparingInt(AgentMemory::getSalience).reversed())
                .limit(WIKI_PAGES)
                .toList();
        for (AgentMemory memory : promotable) {
            MemoryWikiPage page = upsertPage(tenant, memory);
            if (page != null) {
                pageByMemory.put(memory.getId(), page);
                memory.setWikiSlug(page.getSlug());
            }
        }
        int promoted = pageByMemory.size();
        int pageCount = rebuildIndexAndLinks(tenant, new ArrayList<>(pageByMemory.values()));
        int linkCount = links.findByTenantId(tenant).size();

        List<AgentMemory> injected = active.stream()
                .filter(memory -> "ACTIVE".equals(memory.getStatus()))
                .sorted(Comparator.comparingInt(AgentMemory::getSalience).reversed())
                .limit(INJECTED_NOTES)
                .toList();
        int tokens = 0;
        List<MemoryDtos.MemoryView> injectedViews = new ArrayList<>();
        for (AgentMemory memory : injected) {
            int estimate = estimateTokens(memory.getMemoryKey() + " " + memory.getContent());
            if (tokens + estimate > CONTEXT_BUDGET_TOKENS && !injectedViews.isEmpty()) {
                break;
            }
            tokens += estimate;
            injectedViews.add(memoryService.viewOf(memory));
        }
        MemoryContextSnapshot snapshot = new MemoryContextSnapshot();
        snapshot.setTenantId(tenant);
        snapshot.setLayer("INJECTED");
        snapshot.setTokenEstimate(tokens);
        snapshot.setPayloadJson(json(Map.of("injected", injectedViews,
                "wikiPages", pages.findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(tenant, "ACTIVE"))));
        snapshots.save(snapshot);

        run.setMergedMemories(merged);
        run.setCorrectedMemories(corrected);
        run.setPromotedNotes(promoted);
        run.setWikiPages(pageCount);
        run.setWikiLinks(linkCount);
        run.setContextTokens(tokens);
        run.setStatus("COMPLETED");
        run.setMessage("scanned=" + active.size() + " transcripts=" + recent.size()
                + " merged=" + merged + " corrected=" + corrected
                + " promoted=" + promoted + " tokens=" + tokens);
        run.setFinishedAt(Instant.now());
        dreams.save(run);
        audit.record("MEMORY_DREAM", "SUCCESS", "MEMORY_DREAM_RUN", run.getId(), null, run.getMessage());
        return dreamView(run);
    }

    private int consolidate(List<AgentMemory> active) {
        Map<String, List<AgentMemory>> byKey = new LinkedHashMap<>();
        for (AgentMemory memory : active) {
            byKey.computeIfAbsent(memory.getScope() + "|" + memory.getScopeKey() + "|"
                    + memory.getMemoryKey().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(memory);
        }
        int merged = 0;
        for (List<AgentMemory> group : byKey.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (int i = 0; i < group.size(); i++) {
                AgentMemory left = group.get(i);
                if (!"ACTIVE".equals(left.getStatus())) {
                    continue;
                }
                for (int j = i + 1; j < group.size(); j++) {
                    AgentMemory right = group.get(j);
                    if (!"ACTIVE".equals(right.getStatus())) {
                        continue;
                    }
                    double similarity = embeddings.similarity(
                            embeddings.embed(left.getContent()), embeddings.embed(right.getContent()));
                    AgentMemory keeper = compare(left, right) >= 0 ? left : right;
                    AgentMemory duplicate = keeper == left ? right : left;
                    if (similarity >= 0.86) {
                        keeper.setContent(longer(keeper.getContent(), duplicate.getContent()));
                        keeper.setReinforcementCount(keeper.getReinforcementCount()
                                + duplicate.getReinforcementCount());
                        keeper.setConfidence(Math.min(100, Math.max(keeper.getConfidence(),
                                duplicate.getConfidence()) + 2));
                        keeper.setImportance(Math.max(keeper.getImportance(), duplicate.getImportance()));
                        merged++;
                    } else {
                        keeper.setEvidence(mergeEvidence(keeper.getEvidence(),
                                "supersedes:" + duplicate.getId() + " similarity=" + Math.round(similarity * 100)));
                    }
                    duplicate.setStatus("SUPERSEDED");
                    duplicate.setSupersedesId(keeper.getId());
                    if (keeper == right) {
                        break;
                    }
                }
            }
        }
        return merged;
    }

    private int compare(AgentMemory left, AgentMemory right) {
        if (left.isValidated() != right.isValidated()) {
            return left.isValidated() ? 1 : -1;
        }
        if (left.getConfidence() != right.getConfidence()) {
            return left.getConfidence() - right.getConfidence();
        }
        if (left.getReinforcementCount() != right.getReinforcementCount()) {
            return left.getReinforcementCount() - right.getReinforcementCount();
        }
        return left.getUpdatedAt().compareTo(right.getUpdatedAt());
    }

    private int correctAgainstTranscripts(List<AgentMemory> active, List<QueryExecution> recent) {
        Set<String> successfulSql = new LinkedHashSet<>();
        Set<String> failedSql = new LinkedHashSet<>();
        for (QueryExecution execution : recent) {
            String sql = execution.getSqlText() == null ? "" : execution.getSqlText().toLowerCase(Locale.ROOT);
            if (sql.isBlank()) {
                continue;
            }
            if ("SUCCESS".equalsIgnoreCase(execution.getStatus())) {
                successfulSql.add(sql);
            } else if ("FAILED".equalsIgnoreCase(execution.getStatus())
                    || "ERROR".equalsIgnoreCase(execution.getStatus())) {
                failedSql.add(sql);
            }
        }
        int corrected = 0;
        for (AgentMemory memory : active) {
            String content = memory.getContent() == null ? "" : memory.getContent().toLowerCase(Locale.ROOT);
            boolean transientFact = content.matches(".*(当前|今天|今日|最新|临时|暂时|current|temporary|right now).*");
            if (transientFact) {
                memory.setSalience(Math.max(0, memory.getSalience() - 18));
                memory.setLayer("TRANSCRIPT");
                corrected++;
            }
            if (content.contains("sql") || content.contains("join") || content.contains("查询")) {
                boolean successSeen = successfulSql.stream().anyMatch(sql -> overlap(content, sql) >= 2);
                boolean failureSeen = failedSql.stream().anyMatch(sql -> overlap(content, sql) >= 2);
                if (successSeen && !failureSeen) {
                    memory.setValidated(true);
                    memory.setLastValidatedAt(Instant.now());
                    memory.setConfidence(Math.min(100, memory.getConfidence() + 4));
                    memory.setEvidence(mergeEvidence(memory.getEvidence(), "validated-by-recent-transcript"));
                    corrected++;
                } else if (failureSeen) {
                    memory.setEvidence(mergeEvidence(memory.getEvidence(), "conflicts-with-recent-failure"));
                    memory.setConfidence(Math.max(20, memory.getConfidence() - 5));
                    corrected++;
                }
            }
            if (content.contains("已确认") || content.contains("已验证") || content.contains("confirmed")) {
                memory.setValidated(true);
                memory.setLastValidatedAt(Instant.now());
            }
        }
        return corrected;
    }

    private void scoreSalience(AgentMemory memory) {
        double ageDays = memory.getUpdatedAt() == null ? 30
                : ChronoUnit.DAYS.between(memory.getUpdatedAt(), Instant.now());
        double recency = Math.max(0, 10 - Math.min(10, ageDays / 3));
        int score = (int) Math.round(
                memory.getImportance() * 0.35
                        + memory.getConfidence() * 0.30
                        + Math.min(100, memory.getReinforcementCount() * 10) * 0.10
                        + Math.min(100, memory.getUseCount() * 15) * 0.10
                        + (memory.isValidated() ? 10 : 0)
                        + recency - ("TRANSCRIPT".equals(memory.getLayer()) ? 12 : 0));
        memory.setSalience(Math.max(0, Math.min(100, score)));
    }

    private MemoryWikiPage upsertPage(String tenant, AgentMemory memory) {
        String slug = slug(memory.getMemoryKey());
        if (slug.isBlank()) {
            return null;
        }
        MemoryWikiPage page = pages.findByTenantIdAndSlug(tenant, slug).orElseGet(MemoryWikiPage::new);
        boolean created = page.getId() == null;
        page.setTenantId(tenant);
        page.setSlug(slug);
        page.setTitle(memory.getMemoryKey());
        page.setSummary(truncate(memory.getContent(), 220));
        page.setContent("# " + memory.getMemoryKey() + "\n\n" + memory.getContent()
                + "\n\n## Provenance\n- memory: " + memory.getId()
                + "\n- kind: " + memory.getKind()
                + "\n- confidence: " + memory.getConfidence()
                + "\n- evidence: " + (memory.getEvidence() == null ? "session-note" : memory.getEvidence()));
        page.setPageType(pageType(memory.getKind()));
        page.setTokenEstimate(estimateTokens(page.getContent()));
        page.setSourceMemoryIds(memory.getId().toString());
        page.setStatus("ACTIVE");
        return pages.save(page);
    }

    private int rebuildIndexAndLinks(String tenant, List<MemoryWikiPage> topicPages) {
        links.deleteByTenantId(tenant);
        MemoryWikiPage index = pages.findByTenantIdAndSlug(tenant, "index").orElseGet(MemoryWikiPage::new);
        index.setTenantId(tenant);
        index.setSlug("index");
        index.setTitle("Memory index");
        index.setPageType("INDEX");
        index.setStatus("ACTIVE");
        StringBuilder content = new StringBuilder("# Memory index\n\n");
        Map<String, List<MemoryWikiPage>> grouped = new LinkedHashMap<>();
        for (MemoryWikiPage page : topicPages) {
            grouped.computeIfAbsent(page.getPageType(), ignored -> new ArrayList<>()).add(page);
        }
        for (Map.Entry<String, List<MemoryWikiPage>> entry : grouped.entrySet()) {
            content.append("## ").append(entry.getKey()).append("\n");
            for (MemoryWikiPage page : entry.getValue()) {
                content.append("- [").append(page.getTitle()).append("](").append(page.getSlug())
                        .append(") — ").append(nullToEmpty(page.getSummary())).append('\n');
            }
        }
        index.setContent(content.toString());
        index.setSummary("Index of memory wiki pages; injected as the tiny top layer.");
        index.setTokenEstimate(estimateTokens(content.toString()));
        index = pages.save(index);
        int count = 1;
        for (MemoryWikiPage page : topicPages) {
            if (page.getId().equals(index.getId())) {
                continue;
            }
            count++;
            saveLink(tenant, index, page, "INDEXES", 90);
        }
        for (int i = 0; i < topicPages.size(); i++) {
            for (int j = i + 1; j < topicPages.size(); j++) {
                MemoryWikiPage left = topicPages.get(i);
                MemoryWikiPage right = topicPages.get(j);
                if (left.getPageType().equals(right.getPageType())) {
                    saveLink(tenant, left, right, "RELATED", 65);
                }
            }
        }
        return count + (topicPages.isEmpty() ? 0 : 0);
    }

    private void saveLink(String tenant, MemoryWikiPage from, MemoryWikiPage to,
                          String relation, int confidence) {
        MemoryWikiLink link = new MemoryWikiLink();
        link.setTenantId(tenant);
        link.setFromPageId(from.getId());
        link.setToPageId(to.getId());
        link.setRelation(relation);
        link.setConfidence(confidence);
        links.save(link);
    }

    @Transactional(readOnly = true)
    public MemoryDtos.DreamOverview overview() {
        String tenant = tenant();
        List<MemoryDreamRun> runs = dreams.findTop20ByTenantIdOrderByStartedAtDesc(tenant);
        List<MemoryWikiPage> allPages = pages.findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(tenant, "ACTIVE");
        List<MemoryWikiLink> allLinks = links.findByTenantId(tenant);
        long injected = snapshots.findTop20ByTenantIdOrderByCreatedAtDesc(tenant).stream()
                .findFirst().map(MemoryContextSnapshot::getTokenEstimate).orElse(0);
        return new MemoryDtos.DreamOverview(runs.size(), allPages.size(), allLinks.size(), injected,
                runs.isEmpty() ? null : dreamView(runs.get(0)),
                runs.stream().map(this::dreamView).toList(),
                allPages.stream().map(page -> pageView(page, allLinks)).toList());
    }

    @Transactional(readOnly = true)
    public List<MemoryDtos.WikiPageView> wiki() {
        String tenant = tenant();
        List<MemoryWikiLink> allLinks = links.findByTenantId(tenant);
        return pages.findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(tenant, "ACTIVE").stream()
                .map(page -> pageView(page, allLinks)).toList();
    }

    @Transactional(readOnly = true)
    public MemoryDtos.WikiPageView page(String slug) {
        String tenant = tenant();
        MemoryWikiPage page = pages.findByTenantIdAndSlug(tenant, slug)
                .orElseThrow(() -> ApiException.notFound("记忆 wiki 页面不存在: " + slug));
        return pageView(page, links.findByTenantId(tenant));
    }

    @Transactional
    public MemoryDtos.MemoryContext context(String query, String sessionId,
                                            String domainKey, String datasourceKey, String userKey) {
        String tenant = tenant();
        List<MemoryDtos.MemoryView> notes = memoryService.context(query, domainKey, datasourceKey, userKey);
        List<MemoryWikiPage> wikiPages = embeddings.rank(query, pages
                                .findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(tenant, "ACTIVE"),
                        page -> page.getTitle() + " " + nullToEmpty(page.getSummary()) + " " + page.getContent(), 4)
                .stream().toList();
        List<QueryExecution> recent = transcripts.findTop20ByOrderByCreatedAtDesc();
        List<QueryExecution> matchedTranscripts = embeddings.rank(query, recent,
                        execution -> nullToEmpty(execution.getProblem()) + " " + nullToEmpty(execution.getSqlText()), 3)
                .stream().toList();
        int tokenEstimate = notes.stream().mapToInt(note -> estimateTokens(note.memoryKey() + " " + note.content())).sum();
        List<UUID> touched = notes.stream().map(MemoryDtos.MemoryView::id).toList();
        memoryService.touch(touched);
        MemoryContextSnapshot snapshot = new MemoryContextSnapshot();
        snapshot.setTenantId(tenant);
        snapshot.setSessionId(sessionId);
        snapshot.setQueryText(truncate(query, 2000));
        snapshot.setLayer("CONTEXT");
        snapshot.setTokenEstimate(tokenEstimate);
        snapshot.setPayloadJson(json(Map.of("notes", notes,
                "wiki", wikiPages.stream().map(page -> page.getSlug()).toList())));
        snapshots.save(snapshot);
        return new MemoryDtos.MemoryContext(query, notes, notes,
                wikiPages.stream().map(page -> pageView(page, links.findByTenantId(tenant))).toList(),
                matchedTranscripts.stream().map(this::transcriptView).toList(),
                tokenEstimate, CONTEXT_BUDGET_TOKENS);
    }

    private MemoryDtos.WikiPageView pageView(MemoryWikiPage page, List<MemoryWikiLink> allLinks) {
        int linkCount = (int) allLinks.stream()
                .filter(link -> page.getId().equals(link.getFromPageId())
                        || page.getId().equals(link.getToPageId())).count();
        return new MemoryDtos.WikiPageView(page.getId(), page.getSlug(), page.getTitle(),
                page.getSummary(), page.getContent(), page.getPageType(), page.getTokenEstimate(),
                page.getSourceMemoryIds(), page.getStatus(), linkCount,
                page.getCreatedAt(), page.getUpdatedAt());
    }

    private MemoryDtos.DreamRunView dreamView(MemoryDreamRun run) {
        return new MemoryDtos.DreamRunView(run.getId(), run.getTriggerType(), run.getStatus(),
                run.getScannedTranscripts(), run.getScannedMemories(), run.getMergedMemories(),
                run.getCorrectedMemories(), run.getPromotedNotes(), run.getWikiPages(),
                run.getWikiLinks(), run.getContextTokens(), run.getMessage(),
                run.getStartedAt(), run.getFinishedAt());
    }

    private MemoryDtos.ContextTranscript transcriptView(QueryExecution execution) {
        return new MemoryDtos.ContextTranscript(execution.getId(),
                truncate(execution.getSqlText(), 220), execution.getStatus(),
                execution.getDurationMs(), execution.getCreatedAt());
    }

    private static String pageType(String kind) {
        if (kind == null) {
            return "TOPIC";
        }
        return switch (kind.toUpperCase(Locale.ROOT)) {
            case "PREFERENCE" -> "PREFERENCE";
            case "DECISION", "PATTERN" -> "PLAYBOOK";
            case "OPEN_QUESTION" -> "OPEN_QUESTION";
            default -> "TOPIC";
        };
    }

    private static String slug(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static int estimateTokens(String text) {
        return Math.max(1, (text == null ? 0 : text.length()) / 4);
    }

    private static int overlap(String left, String right) {
        String[] tokens = left.split("[^a-z0-9_]+");
        int count = 0;
        for (String token : tokens) {
            if (token.length() >= 4 && right.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private static String mergeEvidence(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        return left + ";" + right;
    }

    private static String longer(String left, String right) {
        return left != null && left.length() >= (right == null ? 0 : right.length()) ? left : right;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{}";
        }
    }

    private static String tenant() {
        return AuthContext.currentOrSystem().tenantId();
    }
}
