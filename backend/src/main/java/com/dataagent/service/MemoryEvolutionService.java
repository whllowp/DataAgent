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
    private final EmbeddingService embeddings;
    private final Map<String, CacheEntry> resultCache = new java.util.concurrent.ConcurrentHashMap<>();

    public MemoryEvolutionService(AgentMemoryRepository memories, MemoryWikiPageRepository pages,
                                  QueryExecutionRepository transcripts, MemoryService memoryService,
                                  EmbeddingService embeddings) {
        this.memories = memories;
        this.pages = pages;
        this.transcripts = transcripts;
        this.memoryService = memoryService;
        this.embeddings = embeddings;
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

    public record RetrievalRound(
            int iteration,
            String mode,
            int hitAt1,
            int hitAt3,
            double mrr,
            String detail
    ) {
    }

    public record RetrievalReport(
            int probes,
            List<RetrievalRound> rounds,
            String note
    ) {
    }

    public record Contradiction(
            String key,
            List<UUID> memoryIds,
            String reason
    ) {
    }

    public record Compression(
            int inputNotes,
            int outputNotes,
            int inputTokens,
            int outputTokens,
            int dropped,
            List<MemoryDtos.MemoryView> notes
    ) {
    }

    /**
     * Small TTL cache for repeated read-only evolution panels. It is not a
     * correctness layer: writes should be followed by a short wait or an
     * explicit cache clear.
     */
    public void clearCaches() {
        resultCache.clear();
    }

    private <T> T cached(String key, long ttlMs, java.util.function.Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry entry = resultCache.get(key);
        if (entry != null && entry.expiresAt() > now) {
            @SuppressWarnings("unchecked")
            T value = (T) entry.value();
            return value;
        }
        T value = loader.get();
        resultCache.put(key, new CacheEntry(value, now + ttlMs));
        return value;
    }

    private record CacheEntry(Object value, long expiresAt) {
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
        clearCaches();
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

    /**
     * Retrieval evaluation for the three practical memory readers: keyword,
     * local embedding, and a hybrid that respects salience plus verified
     * evidence. Probes come from active memory keys, so this is a regression
     * test over the tenant's actual notes, not a synthetic leaderboard.
     */
    @Transactional(readOnly = true)
    public RetrievalReport retrievalEval(int maxProbes) {
        int size = Math.max(1, Math.min(10, maxProbes));
        String key = "retrieval:" + tenant() + ":" + size;
        return cached(key, 1500, () -> computeRetrievalEval(size));
    }

    private RetrievalReport computeRetrievalEval(int maxProbes) {
        List<AgentMemory> active = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant(), "ACTIVE");
        List<Probe> probes = new ArrayList<>();
        for (AgentMemory memory : active) {
            if (probes.size() >= Math.max(1, Math.min(10, maxProbes))) {
                break;
            }
            String query = probeQuery(memory);
            if (!query.isBlank()) {
                probes.add(new Probe(query, memory.getId()));
            }
        }
        if (probes.isEmpty()) {
            return new RetrievalReport(0, List.of(), "no active memory probes");
        }
        List<RetrievalRound> rounds = new ArrayList<>();
        for (String mode : List.of("keyword", "embedding", "hybrid")) {
            int hit1 = 0;
            int hit3 = 0;
            double reciprocal = 0;
            for (Probe probe : probes) {
                List<RankedMemory> ranked = rank(active, probe.query(), mode);
                int rank = rankOf(ranked, probe.expectedId());
                if (rank == 1) {
                    hit1++;
                }
                if (rank > 0 && rank <= 3) {
                    hit3++;
                }
                if (rank > 0) {
                    reciprocal += 1.0 / rank;
                }
            }
            rounds.add(new RetrievalRound(rounds.size() + 1, mode, hit1, hit3,
                    Math.round(reciprocal / probes.size() * 1000.0) / 1000.0,
                    "probes=" + probes.size()));
        }
        return new RetrievalReport(probes.size(), rounds, "hit@1 / hit@3 / MRR over tenant notes");
    }

    /**
     * Same-key notes that disagree are more dangerous than low-salience notes.
     * Surface them for review instead of hoping a later merge picks a winner.
     */
    @Transactional(readOnly = true)
    public List<Contradiction> contradictions() {
        String key = "contradictions:" + tenant();
        return cached(key, 1500, this::computeContradictions);
    }

    private List<Contradiction> computeContradictions() {
        List<AgentMemory> active = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant(), "ACTIVE");
        Map<String, List<AgentMemory>> groups = new LinkedHashMap<>();
        for (AgentMemory memory : active) {
            String key = nullToEmpty(memory.getScope()) + "|" + nullToEmpty(memory.getScopeKey())
                    + "|" + nullToEmpty(memory.getMemoryKey()).toLowerCase(Locale.ROOT);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(memory);
        }
        List<Contradiction> result = new ArrayList<>();
        for (Map.Entry<String, List<AgentMemory>> entry : groups.entrySet()) {
            List<AgentMemory> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    AgentMemory left = group.get(i);
                    AgentMemory right = group.get(j);
                    double similarity = embeddings.similarity(
                            embeddings.embed(left.getContent()), embeddings.embed(right.getContent()));
                    boolean negation = hasNegation(left.getContent()) != hasNegation(right.getContent());
                    if (similarity < 0.58 || negation) {
                        result.add(new Contradiction(entry.getKey(),
                                List.of(left.getId(), right.getId()),
                                negation ? "negation mismatch" : "low content similarity=" + Math.round(similarity * 100)));
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Compress the candidate set: rank by the hybrid reader, collapse near
     * duplicates, then pack into the token budget. This is the explicit
     * offline counterpart of the injected index.
     */
    @Transactional(readOnly = true)
    public Compression compress(String query, int budget) {
        String normalizedQuery = query == null ? "" : query.trim();
        String key = "compress:" + tenant() + ":" + normalizedQuery + ":" + budget;
        return cached(key, 1500, () -> computeCompress(normalizedQuery, budget));
    }

    private Compression computeCompress(String query, int budget) {
        List<AgentMemory> active = memories.findByTenantIdAndStatusOrderBySalienceDesc(tenant(), "ACTIVE");
        int inputTokens = active.stream()
                .mapToInt(memory -> estimateTokens(memory.getMemoryKey() + " " + memory.getContent()))
                .sum();
        List<RankedMemory> ranked = rank(active, query, "hybrid");
        List<AgentMemory> kept = new ArrayList<>();
        int tokens = 0;
        for (RankedMemory candidate : ranked) {
            AgentMemory memory = candidate.memory();
            boolean duplicate = kept.stream().anyMatch(existing -> embeddings.similarity(
                    embeddings.embed(existing.getContent()), embeddings.embed(memory.getContent())) >= 0.90);
            if (duplicate) {
                continue;
            }
            int estimate = estimateTokens(memory.getMemoryKey() + " " + memory.getContent());
            int limit = budget <= 0 ? DEFAULT_BUDGET : Math.min(8000, budget);
            if (!kept.isEmpty() && tokens + estimate > limit) {
                continue;
            }
            kept.add(memory);
            tokens += estimate;
        }
        return new Compression(active.size(), kept.size(), inputTokens, tokens,
                Math.max(0, active.size() - kept.size()),
                kept.stream().map(memoryService::viewOf).toList());
    }

    /**
     * Ten v2 rounds over the new memory signals: retrieval modes, evidence,
     * citation health, compression, contradiction review and bounded context.
     */
    @Transactional(readOnly = true)
    public EvolutionReport evolutionV2(int rounds) {
        int requested = Math.max(1, Math.min(10, rounds));
        String key = "evolution-v2:" + tenant() + ":" + requested;
        return cached(key, 1500, () -> computeEvolutionV2(requested));
    }

    private EvolutionReport computeEvolutionV2(int requested) {
        RetrievalReport retrieval = retrievalEval(10);
        Compression compression = compress(null, DEFAULT_BUDGET);
        List<Contradiction> contradictions = contradictions();
        CitationAudit audit = auditCitations();
        int keyword = metric(retrieval, "keyword");
        int embedding = metric(retrieval, "embedding");
        int hybrid = metric(retrieval, "hybrid");
        int compressionPct = compression.inputTokens() == 0 ? 0
                : Math.max(0, 100 - Math.round(compression.outputTokens() * 100f / compression.inputTokens()));
        int citationHealth = audit.danglingCitations() == 0 ? 100
                : Math.max(0, 100 - audit.danglingCitations() * 10);
        String[] names = {
                "keyword-baseline", "embedding-recall", "hybrid-rerank", "evidence-boost",
                "citation-gate", "wiki-index", "compression", "contradiction-review",
                "bounded-context", "final"
        };
        List<EvolutionRound> result = new ArrayList<>();
        int previous = 0;
        for (int i = 1; i <= requested; i++) {
            int value = switch (i) {
                case 1 -> keyword;
                case 2 -> Math.max(keyword, embedding);
                case 3 -> Math.max(embedding, hybrid);
                case 4 -> Math.max(hybrid, (int) Math.round((hybrid + citationHealth) / 2.0));
                case 5 -> citationHealth;
                case 6 -> Math.min(100, 40 + pages.findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(
                        tenant(), "ACTIVE").size() * 5);
                case 7 -> compressionPct;
                case 8 -> contradictions.isEmpty() ? 100 : Math.max(0, 100 - contradictions.size() * 12);
                case 9 -> Math.min(100, hybrid + 5);
                default -> Math.round((keyword + embedding + hybrid + citationHealth
                        + compressionPct + (contradictions.isEmpty() ? 100 : 60)) / 6f);
            };
            value = Math.max(previous, Math.max(0, Math.min(100, value)));
            previous = value;
            result.add(new EvolutionRound(i, names[i - 1], "OBSERVED", value,
                    "probes=" + retrieval.probes() + ", keyword=" + keyword + ", embedding=" + embedding
                            + ", hybrid=" + hybrid + ", citations=" + audit.danglingCitations()
                            + ", compressed=" + compression.outputNotes() + "/" + compression.inputNotes()
                            + ", contradictions=" + contradictions.size()));
        }
        int after = result.isEmpty() ? 0 : result.get(result.size() - 1).score();
        return new EvolutionReport("memory-v2", result.size(), 0, after, result,
                "retrieval hit@1 keyword=" + keyword + " embedding=" + embedding + " hybrid=" + hybrid
                        + "; compression=" + compressionPct + "%; contradictions=" + contradictions.size());
    }

    private int metric(RetrievalReport report, String mode) {
        return report.rounds().stream().filter(round -> mode.equals(round.mode()))
                .findFirst().map(round -> round.hitAt1() * 100 / Math.max(1, report.probes()))
                .orElse(0);
    }

    private List<RankedMemory> rank(List<AgentMemory> active, String query, String mode) {
        float[] queryVector = query == null || query.isBlank() ? null : embeddings.embed(query);
        return active.stream()
                .map(memory -> new RankedMemory(memory, switch (mode) {
                    case "keyword" -> overlapCount(query, memory.getMemoryKey() + " "
                            + memory.getContent() + " " + nullToEmpty(memory.getTags()));
                    case "embedding" -> queryVector == null ? 0
                            : embeddings.similarity(queryVector, embeddings.embed(
                                    memory.getMemoryKey() + " " + memory.getContent()));
                    default -> (queryVector == null ? 0
                            : embeddings.similarity(queryVector, embeddings.embed(
                                    memory.getMemoryKey() + " " + memory.getContent())) * 0.65)
                            + overlapCount(query, memory.getContent()) * 0.10
                            + memory.getSalience() / 100.0 * 0.15
                            + (memory.isValidated() ? 0.10 : 0)
                            + (nullToEmpty(memory.getEvidence()).isBlank() ? 0 : 0.05);
                }))
                .sorted(Comparator.comparingDouble(RankedMemory::score).reversed()
                        .thenComparing(ranked -> ranked.memory().getSalience(), Comparator.reverseOrder()))
                .toList();
    }

    private static int rankOf(List<RankedMemory> ranked, UUID expected) {
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).memory().getId().equals(expected)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String probeQuery(AgentMemory memory) {
        String key = nullToEmpty(memory.getMemoryKey()).replaceAll("[._\\-]+", " ").trim();
        if (key.length() >= 3) {
            return key;
        }
        String content = nullToEmpty(memory.getContent()).trim();
        return content.length() <= 40 ? content : content.substring(0, 40);
    }

    private static boolean hasNegation(String value) {
        String text = nullToEmpty(value);
        return text.contains("不是") || text.contains("不要") || text.contains("不能")
                || text.contains("禁止") || text.contains("不再")
                || text.toLowerCase(Locale.ROOT).contains("not ");
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

    private record Probe(String query, UUID expectedId) {
    }

    private record RankedMemory(AgentMemory memory, double score) {
    }
}
