package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "openlineage_event")
public class OpenLineageEvent extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";

    @Column(name = "event_time")
    private Instant eventTime;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "job_namespace", length = 300)
    private String jobNamespace;

    @Column(name = "job_name", length = 300)
    private String jobName;

    @Column(name = "run_id", length = 200)
    private String runId;

    @Column(length = 300)
    private String producer;

    @Column(name = "schema_url", length = 500)
    private String schemaUrl;

    @Column(name = "inputs_json", columnDefinition = "TEXT")
    private String inputsJson;

    @Column(name = "outputs_json", columnDefinition = "TEXT")
    private String outputsJson;

    @Column(name = "column_lineage_json", columnDefinition = "TEXT")
    private String columnLineageJson;

    @Column(name = "raw_event", columnDefinition = "TEXT")
    private String rawEvent;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getJobNamespace() {
        return jobNamespace;
    }

    public void setJobNamespace(String jobNamespace) {
        this.jobNamespace = jobNamespace;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getSchemaUrl() {
        return schemaUrl;
    }

    public void setSchemaUrl(String schemaUrl) {
        this.schemaUrl = schemaUrl;
    }

    public String getInputsJson() {
        return inputsJson;
    }

    public void setInputsJson(String inputsJson) {
        this.inputsJson = inputsJson;
    }

    public String getOutputsJson() {
        return outputsJson;
    }

    public void setOutputsJson(String outputsJson) {
        this.outputsJson = outputsJson;
    }

    public String getColumnLineageJson() {
        return columnLineageJson;
    }

    public void setColumnLineageJson(String columnLineageJson) {
        this.columnLineageJson = columnLineageJson;
    }

    public String getRawEvent() {
        return rawEvent;
    }

    public void setRawEvent(String rawEvent) {
        this.rawEvent = rawEvent;
    }
}
