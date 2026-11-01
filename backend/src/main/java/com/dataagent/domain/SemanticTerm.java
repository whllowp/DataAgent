package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "semantic_term")
public class SemanticTerm extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String term;

    @Column(columnDefinition = "TEXT")
    private String definition;

    @Column(length = 1000)
    private String synonyms;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "related_metric_id")
    private UUID relatedMetricId;

    @Column(length = 120)
    private String owner;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(String synonyms) {
        this.synonyms = synonyms;
    }

    public UUID getRelatedEntityId() {
        return relatedEntityId;
    }

    public void setRelatedEntityId(UUID relatedEntityId) {
        this.relatedEntityId = relatedEntityId;
    }

    public UUID getRelatedMetricId() {
        return relatedMetricId;
    }

    public void setRelatedMetricId(UUID relatedMetricId) {
        this.relatedMetricId = relatedMetricId;
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
