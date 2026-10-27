package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_entry")
public class KnowledgeEntry {

    @Id
    private UUID id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String problem;

    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    @Column(name = "optimized_sql", columnDefinition = "TEXT")
    private String optimizedSql;

    @Column(length = 32)
    private String dialect;

    @Column(length = 500)
    private String tags;

    @Column(name = "plan_summary", columnDefinition = "TEXT")
    private String planSummary;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "optimization_steps", columnDefinition = "TEXT")
    private String optimizationSteps;

    @Column(name = "before_duration_ms")
    private Long beforeDurationMs;

    @Column(name = "after_duration_ms")
    private Long afterDurationMs;

    @Column(length = 120)
    private String author;

    @Column(name = "source_query_id")
    private UUID sourceQueryId;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getOptimizedSql() {
        return optimizedSql;
    }

    public void setOptimizedSql(String optimizedSql) {
        this.optimizedSql = optimizedSql;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getPlanSummary() {
        return planSummary;
    }

    public void setPlanSummary(String planSummary) {
        this.planSummary = planSummary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getOptimizationSteps() {
        return optimizationSteps;
    }

    public void setOptimizationSteps(String optimizationSteps) {
        this.optimizationSteps = optimizationSteps;
    }

    public Long getBeforeDurationMs() {
        return beforeDurationMs;
    }

    public void setBeforeDurationMs(Long beforeDurationMs) {
        this.beforeDurationMs = beforeDurationMs;
    }

    public Long getAfterDurationMs() {
        return afterDurationMs;
    }

    public void setAfterDurationMs(Long afterDurationMs) {
        this.afterDurationMs = afterDurationMs;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public UUID getSourceQueryId() {
        return sourceQueryId;
    }

    public void setSourceQueryId(UUID sourceQueryId) {
        this.sourceQueryId = sourceQueryId;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
