package com.dataagent.repository;

import com.dataagent.domain.MemoryWikiPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemoryWikiPageRepository extends JpaRepository<MemoryWikiPage, UUID> {

    List<MemoryWikiPage> findByTenantIdAndStatusOrderByPageTypeAscTitleAsc(String tenantId, String status);

    Optional<MemoryWikiPage> findByTenantIdAndSlug(String tenantId, String slug);

    List<MemoryWikiPage> findByTenantIdAndStatus(String tenantId, String status);
}
