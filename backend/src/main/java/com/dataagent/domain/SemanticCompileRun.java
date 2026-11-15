package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "semantic_compile_run")
public class SemanticCompileRun extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";
    @Column(name = "datasource_id")
    private UUID datasourceId;
    @Column(name = "schema_name", length = 200)
    private String schemaName;
    @Column(nullable = false, length = 20)
    private String status = "RUNNING";
    @Column(name = "scanned_tables", nullable = false)
    private int scannedTables;
    @Column(name = "scanned_columns", nullable = false)
    private int scannedColumns;
    @Column(name = "created_terms", nullable = false)
    private int createdTerms;
    @Column(name = "mapped_columns", nullable = false)
    private int mappedColumns;
    @Column(name = "created_joins", nullable = false)
    private int createdJoins;
    @Column(name = "created_attributes", nullable = false)
    private int createdAttributes;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public UUID getDatasourceId() { return datasourceId; }
    public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getScannedTables() { return scannedTables; }
    public void setScannedTables(int scannedTables) { this.scannedTables = scannedTables; }
    public int getScannedColumns() { return scannedColumns; }
    public void setScannedColumns(int scannedColumns) { this.scannedColumns = scannedColumns; }
    public int getCreatedTerms() { return createdTerms; }
    public void setCreatedTerms(int createdTerms) { this.createdTerms = createdTerms; }
    public int getMappedColumns() { return mappedColumns; }
    public void setMappedColumns(int mappedColumns) { this.mappedColumns = mappedColumns; }
    public int getCreatedJoins() { return createdJoins; }
    public void setCreatedJoins(int createdJoins) { this.createdJoins = createdJoins; }
    public int getCreatedAttributes() { return createdAttributes; }
    public void setCreatedAttributes(int createdAttributes) { this.createdAttributes = createdAttributes; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
