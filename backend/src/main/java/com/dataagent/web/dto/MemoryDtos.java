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
            String layer,
            int salience,
            boolean validated,
            Instant lastValidatedAt,
            String evidence,
            UUID supersedesId,
            String wikiSlug,
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

    public record WikiPageView(
            UUID id,
            String slug,
            String title,
            String summary,
            String content,
            String pageType,
            int tokenEstimate,
            String sourceMemoryIds,
            String status,
            int linkCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WikiLinkView(
            UUID fromPageId,
            String fromSlug,
            UUID toPageId,
            String toSlug,
            String relation,
            int confidence
    ) {
    }

    public record DreamRunView(
            UUID id,
            String triggerType,
            String status,
            int scannedTranscripts,
            int scannedMemories,
            int mergedMemories,
            int correctedMemories,
            int promotedNotes,
            int wikiPages,
            int wikiLinks,
            int contextTokens,
            String message,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    public record ContextTranscript(
            UUID queryId,
            String sqlPreview,
            String status,
            long durationMs,
            Instant createdAt
    ) {
    }

    public record DreamOverview(
            long totalRuns,
            long wikiPages,
            long wikiLinks,
            long contextTokens,
            DreamRunView lastRun,
            List<DreamRunView> recentRuns,
            List<WikiPageView> pages
    ) {
    }

    public record MemoryContext(
            String query,
            List<MemoryView> injected,
            List<MemoryView> notes,
            List<WikiPageView> wiki,
            List<ContextTranscript> transcripts,
            int tokenEstimate,
            int tokenBudget
    ) {
    }
}
