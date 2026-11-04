package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MemoryCandidateDtos {

    private MemoryCandidateDtos() {
    }

    public record View(
            UUID id,
            String sessionId,
            String question,
            String sourceText,
            String scope,
            String scopeKey,
            String kind,
            String memoryKey,
            String content,
            int confidence,
            int importance,
            String tags,
            String reason,
            String status,
            UUID memoryId,
            String reviewedBy,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record Message(String role, String content) {
    }

    public record ExtractRequest(
            @Size(max = 200) String sessionId,
            @Size(max = 20000) String summary,
            @Size(max = 120) String domain,
            List<Message> messages
    ) {
    }

    public record ApproveRequest(
            Boolean force,
            Boolean promoteToKnowledge,
            @Size(max = 300) String knowledgeTitle,
            @Size(max = 100000) String sqlText,
            @Size(max = 120) String reviewedBy
    ) {
    }

    public record ApproveResult(
            View candidate,
            MemoryDtos.MemoryView memory,
            KnowledgeDtos.KnowledgeView knowledge,
            List<MemoryDtos.MemoryView> conflicts,
            boolean approved
    ) {
    }

    public record RejectRequest(@Size(max = 120) String reviewedBy, @Size(max = 1000) String reason) {
    }

    public record ExtractResult(int extracted, List<View> candidates) {
    }

    public record CandidateList(List<View> items) {
    }

    public record RejectResult(View candidate) {
    }
}
