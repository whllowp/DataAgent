package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "memory_context_snapshot")
public class MemoryContextSnapshot extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;
    @Column(name = "session_id", length = 200)
    private String sessionId;
    @Column(name = "query_text", length = 2000)
    private String queryText;
    @Column(nullable = false, length = 30)
    private String layer = "INJECTED";
    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public int getTokenEstimate() { return tokenEstimate; }
    public void setTokenEstimate(int tokenEstimate) { this.tokenEstimate = tokenEstimate; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
