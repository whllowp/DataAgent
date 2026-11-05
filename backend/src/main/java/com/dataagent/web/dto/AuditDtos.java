package com.dataagent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record View(
            UUID id,
            String actor,
            String action,
            String resourceType,
            String resourceId,
            UUID datasourceId,
            String status,
            String detail,
            String clientIp,
            String userAgent,
            Instant createdAt
    ) {
    }

    public record ActionCount(String action, long count) {
    }

    public record Overview(
            long last24h,
            long failed24h,
            long blocked24h,
            List<ActionCount> topActions
    ) {
    }

    public record Page(List<View> items, long matched, int limit, int offset) {
    }
}
