package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "semantic_entity")
public class SemanticEntity extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 120)
    private String domain;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "physical_tables", length = 1000)
    private String physicalTables;

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

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPhysicalTables() {
        return physicalTables;
    }

    public void setPhysicalTables(String physicalTables) {
        this.physicalTables = physicalTables;
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
