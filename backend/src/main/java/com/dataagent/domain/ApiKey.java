package com.dataagent.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_key")
public class ApiKey extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 24)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_datasource", joinColumns = @JoinColumn(name = "api_key_id"))
    @Column(name = "datasource_id")
    private Set<UUID> datasourceIds = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Set<UUID> getDatasourceIds() {
        return datasourceIds;
    }

    public void setDatasourceIds(Set<UUID> datasourceIds) {
        this.datasourceIds = datasourceIds == null ? new HashSet<>() : datasourceIds;
    }
}
