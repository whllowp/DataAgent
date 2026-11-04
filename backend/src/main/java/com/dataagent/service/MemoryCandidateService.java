package com.dataagent.service;

import com.dataagent.domain.MemoryCandidate;
import com.dataagent.repository.MemoryCandidateRepository;
import com.dataagent.web.dto.MemoryCandidateDtos;
import com.dataagent.web.dto.MemoryDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MemoryCandidateService {

    private static final int MAX_CONTENT = 1200;
    private static final List<String> CUES = List.of(
            "默认", "口径", "定义", "必须", "不要", "不能", "统一", "以后", "记住",
            "决策", "规则", "约定", "注意", "禁止", "待确认", "是否");

    private final MemoryCandidateRepository candidates;
    private final MemoryService memories;

    public MemoryCandidateService(MemoryCandidateRepository candidates, MemoryService memories) {
        this.candidates = candidates;
        this.memories = memories;
    }

    @Transactional(readOnly = true)
    public List<MemoryCandidateDtos.View> list(String status) {
        List<MemoryCandidate> items = status == null || status.isBlank()
                ? candidates.findAllByOrderByCreatedAtDesc()
                : candidates.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        return items.stream().map(this::view).toList();
    }

    @Transactional
    public MemoryCandidateDtos.ExtractResult extract(MemoryCandidateDtos.ExtractRequest request) {
        String sessionId = request.sessionId();
        String domain = request.domain();
        List<String> pieces = new ArrayList<>();
        if (request.summary() != null && !request.summary().isBlank()) {
            pieces.add(request.summary());
        }
        String question = null;
        if (request.messages() != null) {
            for (MemoryCandidateDtos.Message message : request.messages()) {
                if (message.content() == null || message.content().isBlank()) {
                    continue;
                }
                if (question == null && "user".equalsIgnoreCase(message.role())) {
                    question = truncate(message.content().trim(), 2000);
                }
                pieces.add(message.content());
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryCandidate> created = new ArrayList<>();
        for (String piece : pieces) {
            for (String sentence : sentences(piece)) {
                String content = sentence.replaceAll("\\s+", " ").trim();
                if (content.length() < 8 || content.length() > MAX_CONTENT
                        || content.startsWith("```") || content.startsWith("SELECT ")
                        || content.startsWith("sql:")) {
                    continue;
                }
                String cue = CUES.stream().filter(content::contains).findFirst().orElse(null);
                if (cue == null) {
                    continue;
                }
                String hash = sha256(content);
                if (!seen.add(hash) || candidates.findByContentHash(hash).isPresent()) {
                    continue;
                }
                MemoryCandidate candidate = new MemoryCandidate();
                candidate.setSessionId(sessionId);
                candidate.setQuestion(question);
                candidate.setSourceText(truncate(content, 8000));
                candidate.setScope(domain == null || domain.isBlank() ? "GLOBAL" : "DOMAIN");
                candidate.setScopeKey(domain == null || domain.isBlank() ? null : domain.trim());
                candidate.setKind(inferKind(content));
                candidate.setMemoryKey("auto." + hash.substring(0, 16));
                candidate.setContent(content);
                candidate.setContentHash(hash);
                candidate.setConfidence(60);
                candidate.setImportance(55);
                candidate.setTags("auto,conversation");
                candidate.setReason("对话抽取命中: " + cue);
                candidate.setStatus("PENDING");
                created.add(candidates.save(candidate));
            }
        }
        return new MemoryCandidateDtos.ExtractResult(created.size(),
                created.stream().map(this::view).toList());
    }

    @Transactional
    public MemoryCandidateDtos.ApproveResult approve(UUID id, MemoryCandidateDtos.ApproveRequest request) {
        MemoryCandidate candidate = required(id);
        if (!"PENDING".equals(candidate.getStatus())) {
            throw new IllegalArgumentException("候选状态不是 PENDING: " + candidate.getStatus());
        }
        List<MemoryDtos.MemoryView> conflicts = memories.conflicts(
                candidate.getScope(), candidate.getScopeKey(), candidate.getMemoryKey(), candidate.getContent());
        boolean force = Boolean.TRUE.equals(request == null ? null : request.force());
        if (!conflicts.isEmpty() && !force) {
            return new MemoryCandidateDtos.ApproveResult(view(candidate), null, null, conflicts, false);
        }
        MemoryDtos.CaptureResult result = memories.capture(new MemoryDtos.CaptureRequest(
                candidate.getScope(),
                candidate.getScopeKey(),
                candidate.getKind(),
                candidate.getMemoryKey(),
                candidate.getContent(),
                "CONVERSATION",
                candidate.getConfidence(),
                candidate.getImportance(),
                candidate.getTags(),
                request == null || request.reviewedBy() == null ? "memory-review" : request.reviewedBy(),
                null,
                request != null && Boolean.TRUE.equals(request.promoteToKnowledge()),
                request == null ? null : request.knowledgeTitle(),
                request == null ? null : request.sqlText()
        ));
        candidate.setStatus("APPROVED");
        candidate.setMemoryId(result.memory().id());
        candidate.setReviewedBy(request == null ? "memory-review" : request.reviewedBy());
        candidate.setReviewedAt(Instant.now());
        candidates.save(candidate);
        return new MemoryCandidateDtos.ApproveResult(view(candidate), result.memory(),
                result.knowledge(), result.conflicts(), true);
    }

    @Transactional
    public MemoryCandidateDtos.RejectResult reject(UUID id, MemoryCandidateDtos.RejectRequest request) {
        MemoryCandidate candidate = required(id);
        candidate.setStatus("REJECTED");
        candidate.setReviewedBy(request == null ? "memory-review" : request.reviewedBy());
        candidate.setReviewedAt(Instant.now());
        if (request != null && request.reason() != null) {
            candidate.setReason(truncate(request.reason(), 500));
        }
        candidates.save(candidate);
        return new MemoryCandidateDtos.RejectResult(view(candidate));
    }

    private MemoryCandidate required(UUID id) {
        return candidates.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记忆候选不存在: " + id));
    }

    private MemoryCandidateDtos.View view(MemoryCandidate candidate) {
        return new MemoryCandidateDtos.View(candidate.getId(), candidate.getSessionId(),
                candidate.getQuestion(), candidate.getSourceText(), candidate.getScope(),
                candidate.getScopeKey(), candidate.getKind(), candidate.getMemoryKey(),
                candidate.getContent(), candidate.getConfidence(), candidate.getImportance(),
                candidate.getTags(), candidate.getReason(), candidate.getStatus(), candidate.getMemoryId(),
                candidate.getReviewedBy(), candidate.getReviewedAt(), candidate.getCreatedAt(),
                candidate.getUpdatedAt());
    }

    private static String inferKind(String content) {
        if (content.contains("待确认") || content.contains("是否") || content.endsWith("？")
                || content.endsWith("?")) {
            return "OPEN_QUESTION";
        }
        if (content.contains("默认") || content.contains("偏好") || content.contains("以后")) {
            return "PREFERENCE";
        }
        if (content.contains("决策") || content.contains("口径") || content.contains("定义")
                || content.contains("统一") || content.contains("必须")) {
            return "DECISION";
        }
        if (content.contains("JOIN") || content.contains("join") || content.contains("SQL")
                || content.contains("查询")) {
            return "PATTERN";
        }
        return "FACT";
    }

    private static List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            for (String sentence : line.split("(?<=[。！？!?；;])")) {
                if (!sentence.isBlank()) {
                    result.add(sentence);
                }
            }
        }
        return result;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
