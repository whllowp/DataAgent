package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "memory_wiki_page")
public class MemoryWikiPage extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;
    @Column(nullable = false, length = 200)
    private String slug;
    @Column(nullable = false, length = 240)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String summary;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "page_type", nullable = false, length = 30)
    private String pageType = "TOPIC";
    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;
    @Column(name = "source_memory_ids", columnDefinition = "TEXT")
    private String sourceMemoryIds;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }
    public int getTokenEstimate() { return tokenEstimate; }
    public void setTokenEstimate(int tokenEstimate) { this.tokenEstimate = tokenEstimate; }
    public String getSourceMemoryIds() { return sourceMemoryIds; }
    public void setSourceMemoryIds(String sourceMemoryIds) { this.sourceMemoryIds = sourceMemoryIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
