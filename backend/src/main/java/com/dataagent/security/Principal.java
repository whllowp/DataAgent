package com.dataagent.security;

import java.util.Set;
import java.util.UUID;

public record Principal(
        String userId,
        String tenantId,
        String role,
        UUID apiKeyId,
        Set<UUID> datasourceIds
) {

    public static final String READ = "READ";
    public static final String EXECUTE = "EXECUTE";
    public static final String ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ADMIN.equalsIgnoreCase(role);
    }

    public boolean canExecute() {
        return isAdmin() || EXECUTE.equalsIgnoreCase(role);
    }

    public boolean canAccess(UUID datasourceId) {
        return isAdmin() || (datasourceIds != null && datasourceIds.contains(datasourceId));
    }

    public static Principal system() {
        return new Principal("system", "default", ADMIN, null, Set.of());
    }

    public static Principal legacyAdmin() {
        return new Principal("demo", "default", ADMIN, null, Set.of());
    }
}
