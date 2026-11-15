package com.dataagent.repository;

import com.dataagent.domain.MemoryWikiLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoryWikiLinkRepository extends JpaRepository<MemoryWikiLink, UUID> {

    List<MemoryWikiLink> findByTenantId(String tenantId);

    List<MemoryWikiLink> findByFromPageId(UUID fromPageId);

    void deleteByTenantId(String tenantId);
}
