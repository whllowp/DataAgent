package com.dataagent.repository;

import com.dataagent.domain.MemoryDreamRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoryDreamRunRepository extends JpaRepository<MemoryDreamRun, UUID> {

    List<MemoryDreamRun> findTop20ByTenantIdOrderByStartedAtDesc(String tenantId);
}
