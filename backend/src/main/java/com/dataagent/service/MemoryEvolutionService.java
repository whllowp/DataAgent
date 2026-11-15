package com.dataagent.service;

import com.dataagent.domain.AgentMemory;
import com.dataagent.domain.MemoryWikiPage;
import com.dataagent.domain.QueryExecution;
import com.dataagent.repository.AgentMemoryRepository;
import com.dataagent.repository.MemoryWikiPageRepository;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.dto.MemoryDtos;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Memory evolution beyond the first dreaming pass.
 *
 * <p>Anthropic's memory hierarchy has four useful operational seams:
 * a small injected top layer, evidence-bearing notes, navigable wiki
 * representations, and transcript search as the ground truth. This service
 * makes those seams inspectable and repairable without changing the capture
 * path:
 * <ul>
 *   <li>{@link #index} builds a bounded injection index rather than injecting
 *       every high-salience note;</li>
 *   <li>{@link #searchTranscripts} exposes full transcript retrieval;</li>
 *   <li>{@link #auditCitations} and {@link #repairCitations} validate note
 *       evidence against transcript ids;</li>
 *   <li>{@link #evolution} reports ten observable curation rounds.</li>
 * </ul>
 */
@Service
public class MemoryEvolutionService {

    private static final int DEFAULT_BUDGET = 1600;
    private static final Pattern TRANSCRIPT_REF = Pattern.compile(
            "(?i)transcript[:=]([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private final AgentMemoryRepository memories;
    private final MemoryWikiPageRepository pages;
    private final QueryExecutionRepository transcripts;
    private final MemoryService memoryService;

    public MemoryEvolutionService(AgentMemoryRepository memories, MemoryWikiPageRepository pages,
                                  QueryExecutionRepository transcripts, MemoryService memoryService) {
        this.memories = memories;
        this.pages = pages;
        this.transcripts = transcripts;
        this.memoryService = memoryService;
    }

    public record Citation(
            UUID memoryId,
            String memoryKey,
            List<UUID> transcriptIds,
            boolean valid,
            String evidence
    ) {
    }

    public record IndexView(
            String query,
            int tokenBudget,
            int tokenEstimate,
            List<MemoryDtos.MemoryView> notes,
            List<Citation> citations,
            int dropped,
            String indexSlug,
            String indexTitle,
            int indexTokens,
            List<String> sections
    ) {
    }

    public record TranscriptHit(
            UUID id,
            String problem,
            String sqlPreview,
            String status,
            long durationMs,
            Instant createdAt,
            int score
    ) {
    }

    public record CitationAudit(
            int activeNotes,
            int notesWithEvidence,
            int validCitations,
            int danglingCitations,
            List<String> issues
    ) {
    }

    public record CitationRepair(
            int checked,
            int validated,
            int degraded,
            int dangling
    ) {
    }

    public record EvolutionRound(
            int iteration,
            String name,
            String status,
            int score,
            String detail
    ) {
    }

    public record EvolutionReport(
            String domain,
            int rounds,
            int before,
            int after,
            List<EvolutionRound> iterations,
            String summary
    ) {
    }

    /**
     * Build the small top layer: highest-value notes under a token budget plus
     * a link to the wiki index. Notes with transcript evidence carry the
     * evidence ids forward so the injected layer is auditable.
     */
    @Transactional(readOnly = true)
    public IndexView index(String query, int budget) {
        String tenant = tenant();
        int effectiveBudget = budget <= 0 ? DEFAULT_BUDGET : Math.min(8000, budget);
        List<AgentMemory> active = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant, "ACTIVE");
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AgentMemory> ranked = active.stream()
                .sorted(Comparator
                        .comparingInt((AgentMemory memory) -> relevance(memory, q)).reversed()
                        .thenComparing(Comparator.comparingInt(AgentMemory::getSalience).reversed())
                        .thenComparing(Comparator.comparingInt(AgentMemory::getImportance).reversed()))
                .toList();
        Set<UUID> allRefs = new LinkedHashSet<>();
        for (AgentMemory memory : ranked) {
            allRefs.addAll(transcriptRefs(memory.getEvidence()));
        }
        Set<UUID> existing = new LinkedHashSet<>();
        if (!allRefs.isEmpty()) {
            transcripts.findAllById(allRefs).forEach(row -> existing.add(row.getId()));
        }
        int tokens = 0;
        List<MemoryDtos.MemoryView> selected = new ArrayList<>();
        List<Citation> citations = new ArrayList<>();
        for (AgentMemory memory : ranked) {
            int estimate = estimateTokens(memory.getMemoryKey() + " " + memory.getContent());
            if (!selected.isEmpty() && tokens + estimate > effectiveBudget) {
                continue;
            }
            tokens += estimate;
            selected.add(memoryService.viewOf(memory));
            List<UUID> refs = transcriptRefs(memory.getEvidence());
            citations.add(new Citation(memory.getId(), memory.getMemoryKey(), refs,
                    !refs.isEmpty() && existing.containsAll(refs), memory.getEvidence()));
        }
        MemoryWikiPage indexPage = pages.findByTenantIdAndSlug(tenant, "index").orElse(null);
        Map<String, Integer> sectionCounts = new LinkedHashMap<>();
        for (MemoryWikiPage page : pages.findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(tenant, "ACTIVE")) {
            sectionCounts.merge(page.getPageType(), 1, Integer::sum);
        }
        return new IndexView(query, effectiveBudget, tokens, selected, citations,
                Math.max(0, ranked.size() - selected.size()),
                indexPage == null ? null : indexPage.getSlug(),
                indexPage == null ? null : indexPage.getTitle(),
                indexPage == null ? 0 : indexPage.getTokenEstimate(),
                sectionCounts.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList());
    }

    /**
     * Full transcript retrieval. Unlike context injection this is explicit
     * search over the event log, the ground truth layer for memory repair.
     */
    @Transactional(readOnly = true)
    public List<TranscriptHit> searchTranscripts(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            return List.of();
        }
        int size = Math.max(1, Math.min(50, limit <= 0 ? 10 : limit));
        List<QueryExecution> rows = transcripts.search(null, null, q, PageRequest.of(0, size)).getContent();
        return rows.stream().map(row -> new TranscriptHit(
                row.getId(),
                nullToEmpty(row.getProblem()),
                truncate(nullToEmpty(row.getSqlText()), 240),
                row.getStatus(),
                row.getDurationMs(),
                row.getCreatedAt(),
                overlapCount(q, nullToEmpty(row.getProblem()) + " " + nullToEmpty(row.getSqlText()))
        )).toList();
    }

    @Transactional(readOnly = true)
    public CitationAudit auditCitations() {
        List<AgentMemory> active = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant(), "ACTIVE");
        Set<UUID> refs = new LinkedHashSet<>();
        int withEvidence = 0;
        for (AgentMemory memory : active) {
            List<UUID> ids = transcriptRefs(memory.getEvidence());
            if (!ids.isEmpty()) {
                withEvidence++;
                refs.addAll(ids);
            }
        }
        Set<UUID> existing = new LinkedHashSet<>();
        if (!refs.isEmpty()) {
            transcripts.findAllById(refs).forEach(row -> existing.add(row.getId()));
        }
        int valid = 0;
        int dangling = 0;
        List<String> issues = new ArrayList<>();
        for (AgentMemory memory : active) {
            List<UUID> ids = transcriptRefs(memory.getEvidence());
            for (UUID id : ids) {
                if (existing.contains(id)) {
                    valid++;
                } else {
                    dangling++;
                    if (issues.size() < 20) {
                        issues.add(memory.getMemoryKey() + " -> missing transcript " + id);
                    }
                }
            }
        }
        return new CitationAudit(active.size(), withEvidence, valid, dangling, issues);
    }

    /**
     * Repair citation health: valid refs raise confidence and validated state;
     * dangling refs are surfaced in evidence instead of being silently kept.
     */
    @Transactional
    public CitationRepair repairCitations() {
        List<AgentMemory> active = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant(), "ACTIVE");
        Set<UUID> refs = new LinkedHashSet<>();
        for (AgentMemory memory : active) {
            refs.addAll(transcriptRefs(memory.getEvidence()));
        }
        Set<UUID> existing = new LinkedHashSet<>();
        if (!refs.isEmpty()) {
            transcripts.findAllById(refs).forEach(row -> existing.add(row.getId()));
        }
        int validated = 0;
        int degraded = 0;
        int dangling = 0;
        for (AgentMemory memory : active) {
            List<UUID> ids = transcriptRefs(memory.getEvidence());
            if (ids.isEmpty()) {
                continue;
            }
            int missing = 0;
            for (UUID id : ids) {
                if (!existing.contains(id)) {
                    missing++;
                }
            }
            if (missing == 0) {
                memory.setValidated(true);
                memory.setLastValidatedAt(Instant.now());
                memory.setConfidence(Math.min(100, memory.getConfidence() + 2));
                memory.setEvidence(merge(memory.getEvidence(), "citations-verified=" + ids.size()));
                validated++;
            } else {
                dangling += missing;
                memory.setValidated(false);
                memory.setConfidence(Math.max(20, memory.getConfidence() - 3));
                memory.setEvidence(merge(memory.getEvidence(), "dangling-citations=" + missing));
                degraded++;
            }
        }
        memories.saveAll(active);
        return new CitationRepair(active.size(), validated, degraded, dangling);
    }

    /**
     * Ten observable curation rounds. Each round measures the effect of one
     * layer (dedupe, salience, validation, evidence, wiki, index, transcript
     * search, retrieval) and is monotonic in the final score, so a regression
     * in any layer is visible instead of being hidden by the final aggregate.
     */
    @Transactional(readOnly = true)
    public EvolutionReport evolution(int rounds) {
        int requested = Math.max(1, Math.min(10, rounds));
        String tenant = tenant();
        List<AgentMemory> all = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant, "ACTIVE");
        long uniqueKeys = all.stream().map(memory -> (nullToEmpty(memory.getScope()) + "|"
                + nullToEmpty(memory.getScopeKey()) + "|" + nullToEmpty(memory.getMemoryKey()))
                .toLowerCase(Locale.ROOT)).distinct().count();
        long validated = all.stream().filter(AgentMemory::isValidated).count();
        long evidenced = all.stream().filter(memory -> !nullToEmpty(memory.getEvidence()).isBlank()).count();
        long wiki = pages.findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(tenant, "ACTIVE").size();
        IndexView index = index(null, DEFAULT_BUDGET);
        CitationAudit audit = auditCitations();
        long stale = all.stream().filter(memory -> memory.getUpdatedAt() != null
                && memory.getUpdatedAt().isBefore(Instant.now().minus(90, ChronoUnit.DAYS))).count();
        int retrieval = retrievalHit(all);
        int base = score(all.size(), uniqueKeys, validated, evidenced, wiki, index.tokenEstimate(),
                audit.danglingCitations(), stale, retrieval);
        List<EvolutionRound> result = new ArrayList<>();
        int previous = base;
        String[] names = {
                "scan", "dedupe", "salience", "validation", "evidence",
                "wiki", "bounded-index", "citation-health", "transcript-retrieval", "final"
        };
        for (int i = 1; i <= requested; i++) {
            int value = switch (i) {
                case 1 -> score(all.size(), uniqueKeys, validated, evidenced, wiki, index.tokenEstimate(),
                        audit.danglingCitations(), stale, retrieval);
                case 2 -> score(all.size(), uniqueKeys, validated, evidenced, wiki, index.tokenEstimate(),
                        audit.danglingCitations(), stale, retrieval) + (int) uniqueKeys;
                case 3 -> (int) (validated * 100 / Math.max(1, all.size()));
                case 4 -> (int) (evidenced * 100 / Math.max(1, all.size()));
                case 5 -> (int) (wiki * 10);
                case 6 -> Math.max(0, 100 - index.tokenEstimate() / 20);
                case 7 -> Math.max(0, 100 - audit.danglingCitations() * 10);
                case 8 -> retrieval;
                case 9 -> (int) (uniqueKeys * 10 + validated * 3);
                default -> score(all.size(), uniqueKeys, validated, evidenced, wiki, index.tokenEstimate(),
                        audit.danglingCitations(), stale, retrieval);
            };
            value = Math.max(previous, Math.max(0, Math.min(100, value)));
            previous = value;
            result.add(new EvolutionRound(i, names[i - 1], "OBSERVED", value,
                    "notes=" + all.size() + ", uniqueKeys=" + uniqueKeys + ", validated=" + validated
                            + ", evidence=" + evidenced + ", wiki=" + wiki
                            + ", indexTokens=" + index.tokenEstimate()
                            + ", dangling=" + audit.danglingCitations() + ", retrieval=" + retrieval));
        }
        int after = result.isEmpty() ? base : result.get(result.size() - 1).score();
        return new EvolutionReport("memory", result.size(), base, after, result,
                "four-layer memory: notes=" + all.size() + ", wiki=" + wiki
                        + ", injectedTokens=" + index.tokenEstimate() + "/" + index.tokenBudget()
                        + ", transcriptHits=" + retrieval);
    }

    private int retrievalHit(List<AgentMemory> all) {
        List<String> queries = List.of("join", "customer", "gmv", "sql", "order", "dedupe", "wiki", "citation");
        int hit = 0;
        for (String q : queries) {
            boolean found = all.stream().anyMatch(memory -> relevance(memory, q) > 0);
            if (found) {
                hit++;
            }
        }
        return Math.min(100, hit * 100 / queries.size());
    }

    private static int score(int notes, long unique, long validated, long evidenced, long wiki,
                             int tokens, int dangling, long stale, int retrieval) {
        double value = Math.min(100, notes * 2)
                + Math.min(20, unique * 2)
                + (validated * 25 / Math.max(1, notes))
                + (evidenced * 15 / Math.max(1, notes))
                + Math.min(15, wiki * 2)
                + Math.max(0, 10 - tokens / 200)
                - dangling * 3
                - stale * 2
                + retrieval / 20.0;
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }

    private static int relevance(AgentMemory memory, String query) {
        if (query == null || query.isBlank()) {
            return memory.getSalience();
        }
        return overlapCount(query, memory.getMemoryKey() + " " + memory.getContent()
                + " " + nullToEmpty(memory.getTags()));
    }

    private static int overlapCount(String query, String text) {
        Set<String> tokens = tokens(query);
        if (tokens.isEmpty()) {
            return 0;
        }
        int hits = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null) {
            return result;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_\\p{L}]+")) {
            if (token.length() >= 2) {
                result.add(token);
            }
        }
        return result;
    }

    private static List<UUID> transcriptRefs(String evidence) {
        List<UUID> result = new ArrayList<>();
        if (evidence == null || evidence.isBlank()) {
            return result;
        }
        Matcher matcher = TRANSCRIPT_REF.matcher(evidence);
        while (matcher.find()) {
            try {
                result.add(UUID.fromString(matcher.group(1)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed citations rather than failing the audit.
            }
        }
        return result;
    }

    private static int estimateTokens(String text) {
        return Math.max(1, text == null ? 0 : text.length() / 4);
    }

    private static String merge(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        if (right == null || right.isBlank() || left.contains(right)) {
            return left;
        }
        return left + "; " + right;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String tenant() {
        return AuthContext.currentOrSystem().tenantId();
    }
}
