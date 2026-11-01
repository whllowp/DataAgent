package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "semantic_metric")
public class SemanticMetric extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "display_name", nullable = false, length = 240)
    private String displayName;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "business_definition", columnDefinition = "TEXT")
    private String businessDefinition;

    @Column(columnDefinition = "TEXT")
    private String expression;

    @Column(length = 1000)
    private String dimensions;

    @Column(length = 1000)
    private String synonyms;

    @Column(length = 120)
    private String owner;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getBusinessDefinition() {
        return businessDefinition;
    }

    public void setBusinessDefinition(String businessDefinition) {
        this.businessDefinition = businessDefinition;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(String synonyms) {
        this.synonyms = synonyms;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
