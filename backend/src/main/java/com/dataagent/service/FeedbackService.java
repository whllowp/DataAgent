package com.dataagent.service;

import com.dataagent.domain.AgentFeedback;
import com.dataagent.repository.AgentFeedbackRepository;
import com.dataagent.web.dto.FeedbackDtos;
import com.dataagent.web.dto.MemoryDtos;
import com.dataagent.web.dto.VerifiedQueryDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class FeedbackService {

    private final AgentFeedbackRepository feedbacks;
    private final MemoryService memories;
    private final VerifiedQueryService verifiedQueries;
    private final AuditService audit;

    public FeedbackService(AgentFeedbackRepository feedbacks, MemoryService memories,
                           VerifiedQueryService verifiedQueries, AuditService audit) {
        this.feedbacks = feedbacks;
        this.memories = memories;
        this.verifiedQueries = verifiedQueries;
        this.audit = audit;
    }

    @Transactional
    public FeedbackDtos.Result submit(FeedbackDtos.Request request) {
        AgentFeedback feedback = new AgentFeedback();
        feedback.setSessionId(request.sessionId());
        feedback.setQuestion(request.question().trim());
        feedback.setOriginalSql(request.originalSql());
        feedback.setCorrectedSql(request.correctedSql());
        feedback.setCorrection(request.correction().trim());
        feedback.setRating(request.rating());
        feedback.setNotes(request.notes());
        feedback.setDatasourceId(request.datasourceId());
        feedback.setMetricId(request.metricId());
        feedback.setEntityId(request.entityId());
        feedbacks.save(feedback);

        String key = "feedback." + sha256(request.question() + "|" + request.correction()).substring(0, 16);
        String scope = request.domain() == null || request.domain().isBlank() ? "GLOBAL" : "DOMAIN";
        MemoryDtos.CaptureResult memoryResult = memories.capture(new MemoryDtos.CaptureRequest(
                scope,
                request.domain(),
                "DECISION",
                key,
                request.correction().trim(),
                "FEEDBACK",
                85,
                75,
                "feedback,correction",
                request.reviewer(),
                null,
                false,
                null,
                null
        ));
        feedback.setMemoryId(memoryResult.memory().id());

        VerifiedQueryDtos.View verified = null;
        if (request.correctedSql() != null && !request.correctedSql().isBlank()) {
            verified = verifiedQueries.create(new VerifiedQueryDtos.Request(
                    request.question().trim(),
                    request.correctedSql(),
                    request.datasourceId(),
                    request.metricId(),
                    request.entityId(),
                    "feedback,correction",
                    "DRAFT",
                    request.reviewer(),
                    request.notes() == null ? request.correction() : request.notes(),
                    null
            ));
            feedback.setVerifiedQueryId(verified.id());
        }
        feedbacks.save(feedback);
        audit.record("FEEDBACK_SUBMIT", "SUCCESS", "FEEDBACK", feedback.getId(), request.datasourceId(),
                "memoryId=" + feedback.getMemoryId() + " verifiedQueryId=" + feedback.getVerifiedQueryId());
        return new FeedbackDtos.Result(view(feedback), memoryResult.memory(), verified,
                memoryResult.conflicts());
    }

    @Transactional(readOnly = true)
    public List<FeedbackDtos.View> recent() {
        return feedbacks.findTop50ByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    private FeedbackDtos.View view(AgentFeedback feedback) {
        return new FeedbackDtos.View(feedback.getId(), feedback.getSessionId(), feedback.getQuestion(),
                feedback.getOriginalSql(), feedback.getCorrectedSql(), feedback.getCorrection(),
                feedback.getRating(), feedback.getNotes(), feedback.getDatasourceId(),
                feedback.getMetricId(), feedback.getEntityId(), feedback.getMemoryId(),
                feedback.getVerifiedQueryId(), feedback.getCreatedAt());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
