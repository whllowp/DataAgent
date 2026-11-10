package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lineage_edge")
public class LineageEdge extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;

    @Column(name = "source_table", nullable = false, length = 300)
    private String sourceTable;

    @Column(name = "target_table", nullable = false, length = 300)
    private String targetTable;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(name = "job_name", length = 300)
    private String jobName;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "run_id", length = 200)
    private String runId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }
}
