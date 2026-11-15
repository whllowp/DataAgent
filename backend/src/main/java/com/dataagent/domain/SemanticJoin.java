package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "semantic_join")
public class SemanticJoin extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "from_term_id", nullable = false)
    private UUID fromTermId;
    @Column(name = "to_term_id", nullable = false)
    private UUID toTermId;
    @Column(name = "join_condition", nullable = false, columnDefinition = "TEXT")
    private String joinCondition;
    @Column(name = "relationship_type", nullable = false, length = 40)
    private String relationshipType = "MANY_TO_ONE";
    @Column(nullable = false)
    private int confidence = 70;
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";
    @Column(nullable = false, length = 30)
    private String source = "INFERRED";
    @Column(columnDefinition = "TEXT")
    private String provenance;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getFromTermId() { return fromTermId; }
    public void setFromTermId(UUID fromTermId) { this.fromTermId = fromTermId; }
    public UUID getToTermId() { return toTermId; }
    public void setToTermId(UUID toTermId) { this.toTermId = toTermId; }
    public String getJoinCondition() { return joinCondition; }
    public void setJoinCondition(String joinCondition) { this.joinCondition = joinCondition; }
    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
}
