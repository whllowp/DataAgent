package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verified_query")
public class VerifiedQuery extends BaseEntity {

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    @Column(name = "datasource_id")
    private UUID datasourceId;

    @Column(name = "metric_id")
    private UUID metricId;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false, length = 20)
    private String status = "VERIFIED";

    @Column(length = 120)
    private String owner;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "expected_columns", length = 2000)
    private String expectedColumns;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_validation_status", length = 20)
    private String lastValidationStatus;

    @Column(name = "last_duration_ms")
    private Long lastDurationMs;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public UUID getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(UUID datasourceId) {
        this.datasourceId = datasourceId;
    }

    public UUID getMetricId() {
        return metricId;
    }

    public void setMetricId(UUID metricId) {
        this.metricId = metricId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getExpectedColumns() {
        return expectedColumns;
    }

    public void setExpectedColumns(String expectedColumns) {
        this.expectedColumns = expectedColumns;
    }

    public int getUseCount() {
        return useCount;
    }

    public void setUseCount(int useCount) {
        this.useCount = useCount;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Instant lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public String getLastValidationStatus() {
        return lastValidationStatus;
    }

    public void setLastValidationStatus(String lastValidationStatus) {
        this.lastValidationStatus = lastValidationStatus;
    }

    public Long getLastDurationMs() {
        return lastDurationMs;
    }

    public void setLastDurationMs(Long lastDurationMs) {
        this.lastDurationMs = lastDurationMs;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
