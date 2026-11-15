package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "semantic_term_column")
public class SemanticTermColumn extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";
    @Column(name = "term_id", nullable = false)
    private UUID termId;
    @Column(name = "datasource_id")
    private UUID datasourceId;
    @Column(name = "schema_name", length = 200)
    private String schemaName;
    @Column(name = "table_name", nullable = false, length = 200)
    private String tableName;
    @Column(name = "column_name", nullable = false, length = 200)
    private String columnName;
    @Column(nullable = false, length = 30)
    private String role = "ATTRIBUTE";
    @Column(nullable = false)
    private int confidence = 70;
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";
    @Column(columnDefinition = "TEXT")
    private String provenance;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public UUID getDatasourceId() { return datasourceId; }
    public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
}
