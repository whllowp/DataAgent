package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "memory_wiki_link")
public class MemoryWikiLink extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;
    @Column(name = "from_page_id", nullable = false)
    private UUID fromPageId;
    @Column(name = "to_page_id", nullable = false)
    private UUID toPageId;
    @Column(nullable = false, length = 40)
    private String relation = "RELATED";
    @Column(nullable = false)
    private int confidence = 70;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public UUID getFromPageId() { return fromPageId; }
    public void setFromPageId(UUID fromPageId) { this.fromPageId = fromPageId; }
    public UUID getToPageId() { return toPageId; }
    public void setToPageId(UUID toPageId) { this.toPageId = toPageId; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
}
