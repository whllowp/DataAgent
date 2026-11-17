package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sql_optimization_run")
public class SqlOptimizationRun {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";

    @Column(name = "datasource_id")
    private UUID datasourceId;

    @Column(name = "original_sql", nullable = false, columnDefinition = "TEXT")
    private String originalSql;

    @Column(name = "best_sql", columnDefinition = "TEXT")
    private String bestSql;

    @Column(name = "baseline_ms")
    private Long baselineMs;

    @Column(name = "best_ms")
    private Long bestMs;

    @Column(name = "improvement_pct")
    private Double improvementPct;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int rounds = 10;

    @Column(length = 40)
    private String verdict;

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

    public String getBestSql() {
        return bestSql;
    }

    public void setBestSql(String bestSql) {
        this.bestSql = bestSql;
    }

    public Long getBaselineMs() {
        return baselineMs;
    }

    public void setBaselineMs(Long baselineMs) {
        this.baselineMs = baselineMs;
    }

    public Long getBestMs() {
        return bestMs;
    }

    public void setBestMs(Long bestMs) {
        this.bestMs = bestMs;
    }

    public Double getImprovementPct() {
        return improvementPct;
    }

    public void setImprovementPct(Double improvementPct) {
        this.improvementPct = improvementPct;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
