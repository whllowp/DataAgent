package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "etl_workflow_dependency")
public class EtlWorkflowDependency extends BaseEntity {

    @Column(name = "upstream_workflow_id", nullable = false)
    private UUID upstreamWorkflowId;

    @Column(name = "downstream_workflow_id", nullable = false)
    private UUID downstreamWorkflowId;

    @Column(name = "condition_type", nullable = false, length = 40)
    private String conditionType = "ALL_SUCCESS";

    @Column(name = "lag_seconds", nullable = false)
    private long lagSeconds;

    public UUID getUpstreamWorkflowId() {
        return upstreamWorkflowId;
    }

    public void setUpstreamWorkflowId(UUID upstreamWorkflowId) {
        this.upstreamWorkflowId = upstreamWorkflowId;
    }

    public UUID getDownstreamWorkflowId() {
        return downstreamWorkflowId;
    }

    public void setDownstreamWorkflowId(UUID downstreamWorkflowId) {
        this.downstreamWorkflowId = downstreamWorkflowId;
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
