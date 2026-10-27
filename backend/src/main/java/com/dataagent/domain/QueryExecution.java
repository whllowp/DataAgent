package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_execution")
public class QueryExecution {

    @Id
    private UUID id;

    @Column(name = "datasource_id", nullable = false)
    private UUID datasourceId;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, length = 20)
    private String kind = "QUERY";

    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    @Column(name = "sql_fingerprint", length = 64)
    private String sqlFingerprint;

    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "fetch_ms", nullable = false)
    private long fetchMs;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(nullable = false)
    private boolean truncated;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "plan_raw", columnDefinition = "TEXT")
    private String planRaw;

    @Column(name = "plan_format", length = 20)
    private String planFormat;

    @Column(length = 2000)
    private String problem;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false)
    private boolean solved;

    @Column(name = "knowledge_id")
    private UUID knowledgeId;

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

    public UUID getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(UUID datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getSqlFingerprint() {
        return sqlFingerprint;
    }

    public void setSqlFingerprint(String sqlFingerprint) {
        this.sqlFingerprint = sqlFingerprint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public long getFetchMs() {
        return fetchMs;
    }

    public void setFetchMs(long fetchMs) {
        this.fetchMs = fetchMs;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPlanRaw() {
        return planRaw;
    }

    public void setPlanRaw(String planRaw) {
        this.planRaw = planRaw;
    }

    public String getPlanFormat() {
        return planFormat;
    }

    public void setPlanFormat(String planFormat) {
        this.planFormat = planFormat;
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean isSolved() {
        return solved;
    }

    public void setSolved(boolean solved) {
        this.solved = solved;
    }

    public UUID getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(UUID knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
