package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "etl_task_dependency")
public class EtlTaskDependency extends BaseEntity {

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "upstream_task_id", nullable = false)
    private UUID upstreamTaskId;

    @Column(name = "downstream_task_id", nullable = false)
    private UUID downstreamTaskId;

    @Column(name = "condition_type", nullable = false, length = 40)
    private String conditionType = "ALL_SUCCESS";

    @Column(name = "lag_seconds", nullable = false)
    private long lagSeconds;

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public UUID getUpstreamTaskId() {
        return upstreamTaskId;
    }

    public void setUpstreamTaskId(UUID upstreamTaskId) {
        this.upstreamTaskId = upstreamTaskId;
    }

    public UUID getDownstreamTaskId() {
        return downstreamTaskId;
    }

    public void setDownstreamTaskId(UUID downstreamTaskId) {
        this.downstreamTaskId = downstreamTaskId;
    }

    public String getConditionType() {
        return conditionType;
    }

    public void setConditionType(String conditionType) {
        this.conditionType = conditionType;
    }

    public long getLagSeconds() {
        return lagSeconds;
    }

    public void setLagSeconds(long lagSeconds) {
        this.lagSeconds = lagSeconds;
    }
}
