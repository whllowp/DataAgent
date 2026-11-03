package com.dataagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SemanticDtos {

    private SemanticDtos() {
    }

    public record EntityView(
            UUID id,
            String name,
            String displayName,
            String domain,
            String description,
            String physicalTables,
            String synonyms,
            String owner,
            String tags,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record EntityRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 120) String domain,
            @Size(max = 8000) String description,
            @Size(max = 1000) String physicalTables,
            @Size(max = 1000) String synonyms,
            @Size(max = 120) String owner,
            @Size(max = 500) String tags,
            @Size(max = 20) String status
    ) {
    }

    public record MetricView(
            UUID id,
            String name,
            String displayName,
            UUID entityId,
            String entityName,
            String businessDefinition,
            String expression,
            String dimensions,
            String synonyms,
            String owner,
            String tags,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MetricRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 240) String displayName,
            UUID entityId,
            @Size(max = 8000) String businessDefinition,
            @Size(max = 8000) String expression,
            @Size(max = 1000) String dimensions,
            @Size(max = 1000) String synonyms,
            @Size(max = 120) String owner,
            @Size(max = 500) String tags,
            @Size(max = 20) String status
    ) {
    }

    public record TermView(
            UUID id,
            String term,
            String definition,
            String synonyms,
            UUID relatedEntityId,
            String relatedEntityName,
            UUID relatedMetricId,
            String relatedMetricName,
            String owner,
            String tags,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TermRequest(
            @NotBlank @Size(max = 160) String term,
            @Size(max = 8000) String definition,
            @Size(max = 1000) String synonyms,
            UUID relatedEntityId,
            UUID relatedMetricId,
            @Size(max = 120) String owner,
            @Size(max = 500) String tags,
            @Size(max = 20) String status
    ) {
    }

    public record LinkView(
            UUID id,
            String sourceType,
            UUID sourceId,
            String targetType,
            UUID targetId,
            String targetName,
            int confidence,
            String reason,
            String linkSource,
            Instant updatedAt
    ) {
    }

    public record RelationshipView(
            UUID id,
            String name,
            UUID fromEntityId,
            String fromEntityName,
            UUID toEntityId,
            String toEntityName,
            String relationshipType,
            String joinCondition,
            String description,
            String synonyms,
            String owner,
            String tags,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record RelationshipRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull UUID fromEntityId,
            @NotNull UUID toEntityId,
            @NotBlank @Size(max = 40) String relationshipType,
            @NotBlank @Size(max = 8000) String joinCondition,
            @Size(max = 8000) String description,
            @Size(max = 1000) String synonyms,
            @Size(max = 120) String owner,
            @Size(max = 500) String tags,
            @Size(max = 20) String status
    ) {
    }

    public record SemanticContext(
            String query,
            List<EntityView> entities,
            List<MetricView> metrics,
            List<TermView> terms,
            List<RelationshipView> relationships,
            List<VerifiedQueryDtos.View> verifiedQueries,
            List<MemoryDtos.MemoryView> memories,
            List<KnowledgeDtos.KnowledgeView> knowledge,
            List<SqlAssetDtos.SqlAssetView> assets
    ) {
    }

    public record DesignRequest(
            @NotBlank @Size(max = 8000) String idea,
            @Size(max = 120) String domain,
            UUID datasourceId
    ) {
    }

    public record DesignView(
            String idea,
            String domain,
            List<EntityView> entities,
            List<MetricView> metrics,
            List<TermView> terms,
            List<RelationshipView> relationships,
            List<VerifiedQueryDtos.View> verifiedQueries,
            List<MemoryDtos.MemoryView> memories,
            List<String> designAngles,
            List<String> questions,
            List<String> risks,
            List<KnowledgeDtos.KnowledgeView> relatedKnowledge,
            List<SqlAssetDtos.SqlAssetView> relatedAssets,
            String promptContext
    ) {
    }

    public record SyncRunView(
            UUID id,
            String triggerType,
            String status,
            Instant startedAt,
            Instant finishedAt,
            int scannedAssets,
            int scannedKnowledge,
            int createdLinks,
            int updatedLinks,
            int removedLinks,
            String message
    ) {
    }

    public record SemanticOverview(
            long entities,
            long metrics,
            long terms,
            long links,
            long knowledgeLinked,
            long assetsLinked,
            SyncRunView lastSync,
            List<SyncRunView> recentRuns
    ) {
    }
}
