package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sql_benchmark_case")
public class SqlBenchmarkCase extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 120)
    private String domain;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    @Column(name = "datasource_id")
    private UUID datasourceId;

    @Column(name = "baseline_ms")
    private Long baselineMs;

    @Column(name = "threshold_ms")
    private Long thresholdMs;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_duration_ms")
    private Long lastDurationMs;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Long getBaselineMs() {
        return baselineMs;
    }

    public void setBaselineMs(Long baselineMs) {
        this.baselineMs = baselineMs;
    }

    public Long getThresholdMs() {
        return thresholdMs;
    }

    public void setThresholdMs(Long thresholdMs) {
        this.thresholdMs = thresholdMs;
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

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public Long getLastDurationMs() {
        return lastDurationMs;
    }

    public void setLastDurationMs(Long lastDurationMs) {
        this.lastDurationMs = lastDurationMs;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
