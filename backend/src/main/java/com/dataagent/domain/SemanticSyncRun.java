package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "semantic_sync_run")
public class SemanticSyncRun {

    @Id
    private UUID id;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "scanned_assets", nullable = false)
    private int scannedAssets;

    @Column(name = "scanned_knowledge", nullable = false)
    private int scannedKnowledge;

    @Column(name = "created_links", nullable = false)
    private int createdLinks;

    @Column(name = "updated_links", nullable = false)
    private int updatedLinks;

    @Column(name = "removed_links", nullable = false)
    private int removedLinks;

    @Column(columnDefinition = "TEXT")
    private String message;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (status == null) {
            status = "RUNNING";
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getScannedAssets() {
        return scannedAssets;
    }

    public void setScannedAssets(int scannedAssets) {
        this.scannedAssets = scannedAssets;
    }

    public int getScannedKnowledge() {
        return scannedKnowledge;
    }

    public void setScannedKnowledge(int scannedKnowledge) {
        this.scannedKnowledge = scannedKnowledge;
    }

    public int getCreatedLinks() {
        return createdLinks;
    }

    public void setCreatedLinks(int createdLinks) {
        this.createdLinks = createdLinks;
    }

    public int getUpdatedLinks() {
        return updatedLinks;
    }

    public void setUpdatedLinks(int updatedLinks) {
        this.updatedLinks = updatedLinks;
    }

    public int getRemovedLinks() {
        return removedLinks;
    }

    public void setRemovedLinks(int removedLinks) {
        this.removedLinks = removedLinks;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
