package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sql_asset")
public class SqlAsset extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    @Column(name = "business_problem", columnDefinition = "TEXT")
    private String businessProblem;

    @Column(length = 120)
    private String domain;

    @Column(length = 500)
    private String tags;

    @Column(name = "datasource_id")
    private java.util.UUID datasourceId;

    @Column(length = 120)
    private String author;

    @Column(nullable = false, length = 32)
    private String source = "EDITOR";

    @Column(name = "execution_count", nullable = false)
    private int executionCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "avg_duration_ms")
    private Long avgDurationMs;

    @Column(name = "last_duration_ms")
    private Long lastDurationMs;

    @Column(name = "last_plan", columnDefinition = "TEXT")
    private String lastPlan;

    @Column(name = "last_plan_format", length = 20)
    private String lastPlanFormat;

    @Column(name = "knowledge_id")
    private java.util.UUID knowledgeId;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "semantic_synced_at")
    private Instant semanticSyncedAt;

    @PrePersist
    void onCreate() {
        if (firstSeen == null) {
            firstSeen = Instant.now();
        }
        if (lastSeen == null) {
            lastSeen = firstSeen;
        }
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getBusinessProblem() {
        return businessProblem;
    }

    public void setBusinessProblem(String businessProblem) {
        this.businessProblem = businessProblem;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public java.util.UUID getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(java.util.UUID datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getExecutionCount() {
        return executionCount;
    }

    public void setExecutionCount(int executionCount) {
        this.executionCount = executionCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Long getAvgDurationMs() {
        return avgDurationMs;
    }

    public void setAvgDurationMs(Long avgDurationMs) {
        this.avgDurationMs = avgDurationMs;
    }

    public Long getLastDurationMs() {
        return lastDurationMs;
    }

    public void setLastDurationMs(Long lastDurationMs) {
        this.lastDurationMs = lastDurationMs;
    }

    public String getLastPlan() {
        return lastPlan;
    }

    public void setLastPlan(String lastPlan) {
        this.lastPlan = lastPlan;
    }

    public String getLastPlanFormat() {
        return lastPlanFormat;
    }

    public void setLastPlanFormat(String lastPlanFormat) {
        this.lastPlanFormat = lastPlanFormat;
    }

    public java.util.UUID getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(java.util.UUID knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Instant getSemanticSyncedAt() {
        return semanticSyncedAt;
    }

    public void setSemanticSyncedAt(Instant semanticSyncedAt) {
        this.semanticSyncedAt = semanticSyncedAt;
    }
}
