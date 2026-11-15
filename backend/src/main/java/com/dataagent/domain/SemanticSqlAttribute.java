package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "semantic_sql_attribute")
public class SemanticSqlAttribute extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";
    @Column(name = "term_id", nullable = false)
    private UUID termId;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "display_name", nullable = false, length = 240)
    private String displayName;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String expression;
    @Column(name = "business_definition", columnDefinition = "TEXT")
    private String businessDefinition;
    @Column(length = 200)
    private String grain;
    @Column(length = 1000)
    private String synonyms;
    @Column(length = 120)
    private String owner;
    @Column(length = 500)
    private String tags;
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";
    @Column(nullable = false)
    private int confidence = 70;
    @Column(nullable = false, length = 30)
    private String source = "COMPILED";
    @Column(name = "verified_query_id")
    private UUID verifiedQueryId;
    @Column(name = "usage_count", nullable = false)
    private int usageCount;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(columnDefinition = "TEXT")
    private String provenance;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getBusinessDefinition() { return businessDefinition; }
    public void setBusinessDefinition(String businessDefinition) { this.businessDefinition = businessDefinition; }
    public String getGrain() { return grain; }
    public void setGrain(String grain) { this.grain = grain; }
    public String getSynonyms() { return synonyms; }
    public void setSynonyms(String synonyms) { this.synonyms = synonyms; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public UUID getVerifiedQueryId() { return verifiedQueryId; }
    public void setVerifiedQueryId(UUID verifiedQueryId) { this.verifiedQueryId = verifiedQueryId; }
    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
}
