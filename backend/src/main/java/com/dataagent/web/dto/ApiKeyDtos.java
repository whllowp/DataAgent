package com.dataagent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiKeyDtos {

    private ApiKeyDtos() {
    }

    public record CreateRequest(
            String name,
            String role,
            String tenantId,
            Instant expiresAt,
            List<UUID> datasourceIds
    ) {
    }

    public record View(
            UUID id,
            String name,
            String keyPrefix,
            String role,
            String tenantId,
            boolean enabled,
            Instant expiresAt,
            Instant lastUsedAt,
            List<UUID> datasourceIds,
            Instant createdAt
    ) {
    }

    public record Created(View apiKey, String key) {
    }

    public record Me(String userId, String tenantId, String role, String apiKeyId, List<UUID> datasourceIds) {
    }
}
