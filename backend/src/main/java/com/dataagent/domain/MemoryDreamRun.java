package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "memory_dream_run")
public class MemoryDreamRun extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType = "MANUAL";
    @Column(nullable = false, length = 20)
    private String status = "RUNNING";
    @Column(name = "scanned_transcripts", nullable = false)
    private int scannedTranscripts;
    @Column(name = "scanned_memories", nullable = false)
    private int scannedMemories;
    @Column(name = "merged_memories", nullable = false)
    private int mergedMemories;
    @Column(name = "corrected_memories", nullable = false)
    private int correctedMemories;
    @Column(name = "promoted_notes", nullable = false)
    private int promotedNotes;
    @Column(name = "wiki_pages", nullable = false)
    private int wikiPages;
    @Column(name = "wiki_links", nullable = false)
    private int wikiLinks;
    @Column(name = "context_tokens", nullable = false)
    private int contextTokens;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getScannedTranscripts() { return scannedTranscripts; }
    public void setScannedTranscripts(int scannedTranscripts) { this.scannedTranscripts = scannedTranscripts; }
    public int getScannedMemories() { return scannedMemories; }
    public void setScannedMemories(int scannedMemories) { this.scannedMemories = scannedMemories; }
    public int getMergedMemories() { return mergedMemories; }
    public void setMergedMemories(int mergedMemories) { this.mergedMemories = mergedMemories; }
    public int getCorrectedMemories() { return correctedMemories; }
    public void setCorrectedMemories(int correctedMemories) { this.correctedMemories = correctedMemories; }
    public int getPromotedNotes() { return promotedNotes; }
    public void setPromotedNotes(int promotedNotes) { this.promotedNotes = promotedNotes; }
    public int getWikiPages() { return wikiPages; }
    public void setWikiPages(int wikiPages) { this.wikiPages = wikiPages; }
    public int getWikiLinks() { return wikiLinks; }
    public void setWikiLinks(int wikiLinks) { this.wikiLinks = wikiLinks; }
    public int getContextTokens() { return contextTokens; }
    public void setContextTokens(int contextTokens) { this.contextTokens = contextTokens; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
