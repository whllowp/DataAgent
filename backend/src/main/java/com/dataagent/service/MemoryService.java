package com.dataagent.service;

import com.dataagent.domain.AgentMemory;
import com.dataagent.repository.AgentMemoryRepository;
import com.dataagent.web.dto.KnowledgeDtos;
import com.dataagent.web.dto.MemoryDtos;
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
public class MemoryService {

    private static final int MAX_CONTEXT_MEMORIES = 8;

    private final AgentMemoryRepository memories;
    private final KnowledgeService knowledgeService;

    public MemoryService(AgentMemoryRepository memories, KnowledgeService knowledgeService) {
        this.memories = memories;
        this.knowledgeService = knowledgeService;
    }

    @Transactional(readOnly = true)
    public List<MemoryDtos.MemoryView> list(String q, String scope, String kind, String status) {
        return memories.search(blank(q) ? null : q.trim(),
                        blank(scope) ? null : scope.trim().toUpperCase(Locale.ROOT),
                        blank(kind) ? null : kind.trim().toUpperCase(Locale.ROOT),
                        blank(status) ? null : status.trim().toUpperCase(Locale.ROOT))
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public MemoryDtos.MemoryView get(UUID id) {
        return view(required(id));
    }

    @Transactional
    public MemoryDtos.CaptureResult capture(MemoryDtos.CaptureRequest request) {
        String scope = request.scope().trim().toUpperCase(Locale.ROOT);
        String key = request.key().trim();
        String scopeKey = blank(request.scopeKey()) ? null : request.scopeKey().trim();
        AgentMemory memory = memories.findExact(scope, scopeKey, key).orElseGet(AgentMemory::new);
        boolean created = memory.getId() == null;
        if (created) {
            memory.setScope(scope);
            memory.setScopeKey(scopeKey);
            memory.setKind(request.kind().trim().toUpperCase(Locale.ROOT));
            memory.setMemoryKey(key);
            memory.setSource(blank(request.source()) ? "AGENT" : request.source().trim().toUpperCase(Locale.ROOT));
            memory.setConfidence(clamp(request.confidence(), 70));
            memory.setImportance(clamp(request.importance(), 50));
            memory.setReinforcementCount(1);
        } else {
            memory.setConfidence(Math.min(100, Math.max(memory.getConfidence(), clamp(request.confidence(), memory.getConfidence())) + 3));
            memory.setImportance(Math.max(memory.getImportance(), clamp(request.importance(), memory.getImportance())));
            memory.setReinforcementCount(memory.getReinforcementCount() + 1);
            memory.setStatus("ACTIVE");
        }
        memory.setContent(request.content().trim());
        memory.setTags(merge(memory.getTags(), request.tags()));
        memory.setOwner(blank(request.owner()) ? memory.getOwner() : request.owner().trim());
        memory.setExpiresAt(request.expiresAt());
        KnowledgeDtos.KnowledgeView linkedKnowledge = null;
        if (Boolean.TRUE.equals(request.promoteToKnowledge()) && memory.getKnowledgeId() != null) {
            linkedKnowledge = knowledgeService.get(memory.getKnowledgeId());
        } else if (Boolean.TRUE.equals(request.promoteToKnowledge())
                && !blank(request.sqlText())) {
            String title = blank(request.knowledgeTitle())
                    ? truncate(key, 280) : truncate(request.knowledgeTitle().trim(), 280);
            linkedKnowledge = knowledgeService.create(new KnowledgeDtos.KnowledgeRequest(
                    title,
                    truncate(memory.getContent(), 8000),
                    request.sqlText(),
                    null,
                    null,
                    merge(memory.getTags(), "长期记忆"),
                    null,
                    "由长期记忆提升：" + key,
                    "从 Agent 对话/运行中沉淀，来源：" + memory.getSource(),
                    null,
                    null,
                    blank(request.owner()) ? "agent-memory" : request.owner().trim(),
                    null
            ), blank(request.owner()) ? "agent-memory" : request.owner().trim());
            memory.setKnowledgeId(linkedKnowledge.id());
        }
        memories.save(memory);
        return new MemoryDtos.CaptureResult(view(memory), linkedKnowledge);
    }

    @Transactional
    public MemoryDtos.MemoryView update(UUID id, MemoryDtos.CaptureRequest request) {
        AgentMemory memory = required(id);
        memory.setContent(request.content().trim());
        memory.setKind(request.kind().trim().toUpperCase(Locale.ROOT));
        memory.setConfidence(clamp(request.confidence(), memory.getConfidence()));
        memory.setImportance(clamp(request.importance(), memory.getImportance()));
        memory.setTags(merge(memory.getTags(), request.tags()));
        memory.setOwner(blank(request.owner()) ? memory.getOwner() : request.owner().trim());
        memory.setExpiresAt(request.expiresAt());
        return view(memories.save(memory));
    }

    @Transactional
    public void delete(UUID id) {
        memories.delete(required(id));
    }

    @Transactional
    public MemoryDtos.MemoryView use(UUID id) {
        AgentMemory memory = required(id);
        memory.setUseCount(memory.getUseCount() + 1);
        memory.setLastUsedAt(Instant.now());
        return view(memories.save(memory));
    }

    @Transactional
    public MemoryDtos.MemoryView archive(UUID id) {
        AgentMemory memory = required(id);
        memory.setStatus("ARCHIVED");
        return view(memories.save(memory));
    }

    @Transactional
    public MemoryDtos.ArchiveResult archiveExpired() {
        List<AgentMemory> expired = memories.findByStatusAndExpiresAtBefore("ACTIVE", Instant.now());
        expired.forEach(memory -> memory.setStatus("ARCHIVED"));
        memories.saveAll(expired);
        return new MemoryDtos.ArchiveResult(expired.size());
    }

    @Transactional(readOnly = true)
    public MemoryDtos.MemoryOverview overview() {
        List<AgentMemory> all = memories.findAll();
        List<MemoryDtos.MemoryView> recent = all.stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .limit(8)
                .map(this::view)
                .toList();
        return new MemoryDtos.MemoryOverview(all.size(),
                all.stream().filter(item -> "ACTIVE".equals(item.getStatus())).count(),
                all.stream().filter(item -> "ARCHIVED".equals(item.getStatus())).count(),
                all.stream().filter(item -> item.getKnowledgeId() != null).count(),
                recent);
    }

    /**
     * Retrieves long-term memory for the agent context. GLOBAL memory is always
     * eligible; DOMAIN/DATASOURCE/USER memory is matched by key. Query tokens
     * narrow the result, and use counts are updated for observability.
     */
    @Transactional(readOnly = true)
    public List<MemoryDtos.MemoryView> context(String query, String domainKey,
                                               String datasourceKey, String userKey) {
        List<AgentMemory> candidates = memories.findContextCandidates(
                domainKey == null ? "" : domainKey,
                datasourceKey == null ? "" : datasourceKey,
                userKey == null ? "" : userKey,
                Instant.now());
        Set<String> tokens = tokenize(query);
        return candidates.stream()
                .filter(memory -> tokens.isEmpty() || matches(memory, tokens))
                .limit(MAX_CONTEXT_MEMORIES)
                .map(this::view)
                .toList();
    }

    private boolean matches(AgentMemory memory, Set<String> tokens) {
        String text = (memory.getMemoryKey() + " " + memory.getContent() + " " + nullToEmpty(memory.getTags()))
                .toLowerCase(Locale.ROOT);
        return tokens.stream().anyMatch(text::contains);
    }

    private Set<String> tokenize(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        if (query == null) {
            return tokens;
        }
        for (String token : query.replaceAll("[\\p{Punct}，。；：！？、]+", " ").trim().split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    private AgentMemory required(UUID id) {
        return memories.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("长期记忆不存在: " + id));
    }

    private MemoryDtos.MemoryView view(AgentMemory memory) {
        return new MemoryDtos.MemoryView(memory.getId(), memory.getScope(), memory.getScopeKey(),
                memory.getKind(), memory.getMemoryKey(), memory.getContent(), memory.getSource(),
                memory.getConfidence(), memory.getImportance(), memory.getStatus(), memory.getTags(),
                memory.getOwner(), memory.getUseCount(), memory.getReinforcementCount(),
                memory.getLastUsedAt(), memory.getExpiresAt(), memory.getKnowledgeId(),
                memory.getCreatedAt(), memory.getUpdatedAt());
    }

    private static int clamp(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, Math.min(100, value));
    }

    private static String merge(String left, String right) {
        if (blank(right)) {
            return left;
        }
        if (blank(left)) {
            return right.trim();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String piece : (left + "," + right).split("[,，]")) {
            if (!piece.isBlank()) {
                values.add(piece.trim());
            }
        }
        return String.join(",", values);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
