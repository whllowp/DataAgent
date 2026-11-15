package com.dataagent.repository;

import com.dataagent.domain.MemoryContextSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoryContextSnapshotRepository extends JpaRepository<MemoryContextSnapshot, UUID> {

    List<MemoryContextSnapshot> findTop20ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
