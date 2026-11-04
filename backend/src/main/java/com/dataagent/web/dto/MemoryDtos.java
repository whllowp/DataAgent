package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MemoryDtos {

    private MemoryDtos() {
    }

    public record MemoryView(
            UUID id,
            String scope,
            String scopeKey,
            String kind,
            String memoryKey,
            String content,
            String source,
            int confidence,
            int importance,
            String status,
            String tags,
            String owner,
            int useCount,
            int reinforcementCount,
            Instant lastUsedAt,
            Instant expiresAt,
            UUID knowledgeId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CaptureRequest(
            @NotBlank @Size(max = 20) String scope,
            @Size(max = 200) String scopeKey,
            @NotBlank @Size(max = 30) String kind,
            @NotBlank @Size(max = 300) String key,
            @NotBlank @Size(max = 12000) String content,
            @Size(max = 30) String source,
            Integer confidence,
            Integer importance,
            @Size(max = 500) String tags,
            @Size(max = 120) String owner,
            Instant expiresAt,
            Boolean promoteToKnowledge,
            @Size(max = 300) String knowledgeTitle,
            @Size(max = 100000) String sqlText
    ) {
    }

    public record CaptureResult(MemoryView memory, KnowledgeDtos.KnowledgeView knowledge,
                                List<MemoryView> conflicts) {
    }

    public record MemoryOverview(
            long total,
            long active,
            long archived,
            long knowledgeLinked,
            List<MemoryView> recent
    ) {
    }

    public record ArchiveResult(int archived) {
    }
}
