package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_accumulation_run")
public class KnowledgeAccumulationRun {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(name = "min_frequency", nullable = false)
    private int minFrequency = 3;

    @Column(nullable = false)
    private int promoted;

    @Column(nullable = false)
    private int merged;

    @Column(nullable = false)
    private int deduped;

    @Column(name = "total_knowledge", nullable = false)
    private int totalKnowledge;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public int getMinFrequency() {
        return minFrequency;
    }

    public void setMinFrequency(int minFrequency) {
        this.minFrequency = minFrequency;
    }

    public int getPromoted() {
        return promoted;
    }

    public void setPromoted(int promoted) {
        this.promoted = promoted;
    }

    public int getMerged() {
        return merged;
    }

    public void setMerged(int merged) {
        this.merged = merged;
    }

    public int getDeduped() {
        return deduped;
    }

    public void setDeduped(int deduped) {
        this.deduped = deduped;
    }

    public int getTotalKnowledge() {
        return totalKnowledge;
    }

    public void setTotalKnowledge(int totalKnowledge) {
        this.totalKnowledge = totalKnowledge;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
