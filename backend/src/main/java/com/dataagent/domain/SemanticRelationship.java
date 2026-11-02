package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "semantic_relationship")
public class SemanticRelationship extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "from_entity_id", nullable = false)
    private UUID fromEntityId;

    @Column(name = "to_entity_id", nullable = false)
    private UUID toEntityId;

    @Column(name = "relationship_type", nullable = false, length = 40)
    private String relationshipType;

    @Column(name = "join_condition", nullable = false, columnDefinition = "TEXT")
    private String joinCondition;

    @Column(columnDefinition = "TEXT")
    private String description;

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

    public UUID getFromEntityId() {
        return fromEntityId;
    }

    public void setFromEntityId(UUID fromEntityId) {
        this.fromEntityId = fromEntityId;
    }

    public UUID getToEntityId() {
        return toEntityId;
    }

    public void setToEntityId(UUID toEntityId) {
        this.toEntityId = toEntityId;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getJoinCondition() {
        return joinCondition;
    }

    public void setJoinCondition(String joinCondition) {
        this.joinCondition = joinCondition;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
