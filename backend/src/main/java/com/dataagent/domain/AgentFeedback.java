package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "agent_feedback")
public class AgentFeedback extends BaseEntity {

    @Column(name = "session_id", length = 200)
    private String sessionId;

    @Column(length = 2000)
    private String question;

    @Column(name = "original_sql", columnDefinition = "TEXT")
    private String originalSql;

    @Column(name = "corrected_sql", columnDefinition = "TEXT")
    private String correctedSql;

    @Column(columnDefinition = "TEXT")
    private String correction;

    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "datasource_id")
    private UUID datasourceId;

    @Column(name = "metric_id")
    private UUID metricId;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "memory_id")
    private UUID memoryId;

    @Column(name = "verified_query_id")
    private UUID verifiedQueryId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getOriginalSql() {
        return originalSql;
    }

    public void setOriginalSql(String originalSql) {
        this.originalSql = originalSql;
    }

    public String getCorrectedSql() {
        return correctedSql;
    }

    public void setCorrectedSql(String correctedSql) {
        this.correctedSql = correctedSql;
    }

    public String getCorrection() {
        return correction;
    }

    public void setCorrection(String correction) {
        this.correction = correction;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public UUID getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(UUID memoryId) {
        this.memoryId = memoryId;
    }

    public UUID getVerifiedQueryId() {
        return verifiedQueryId;
    }

    public void setVerifiedQueryId(UUID verifiedQueryId) {
        this.verifiedQueryId = verifiedQueryId;
    }
}
