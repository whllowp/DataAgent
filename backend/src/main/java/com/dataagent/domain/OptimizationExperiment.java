package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "optimization_experiment")
public class OptimizationExperiment {

    @Id
    private UUID id;

    @Column(name = "datasource_id", nullable = false)
    private UUID datasourceId;

    @Column(name = "original_sql", nullable = false, columnDefinition = "TEXT")
    private String originalSql;

    @Column(name = "candidate_sql", nullable = false, columnDefinition = "TEXT")
    private String candidateSql;

    @Column(name = "original_ms")
    private Long originalMs;

    @Column(name = "candidate_ms")
    private Long candidateMs;

    @Column(name = "original_plan", columnDefinition = "TEXT")
    private String originalPlan;

    @Column(name = "candidate_plan", columnDefinition = "TEXT")
    private String candidatePlan;

    @Column(length = 40)
    private String verdict;

    @Column(columnDefinition = "TEXT")
    private String notes;

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

    public String getOriginalSql() {
        return originalSql;
    }

    public void setOriginalSql(String originalSql) {
        this.originalSql = originalSql;
    }

    public String getCandidateSql() {
        return candidateSql;
    }

    public void setCandidateSql(String candidateSql) {
        this.candidateSql = candidateSql;
    }

    public Long getOriginalMs() {
        return originalMs;
    }

    public void setOriginalMs(Long originalMs) {
        this.originalMs = originalMs;
    }

    public Long getCandidateMs() {
        return candidateMs;
    }

    public void setCandidateMs(Long candidateMs) {
        this.candidateMs = candidateMs;
    }

    public String getOriginalPlan() {
        return originalPlan;
    }

    public void setOriginalPlan(String originalPlan) {
        this.originalPlan = originalPlan;
    }

    public String getCandidatePlan() {
        return candidatePlan;
    }

    public void setCandidatePlan(String candidatePlan) {
        this.candidatePlan = candidatePlan;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
