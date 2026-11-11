package com.dataagent.repository;

import com.dataagent.domain.RsiRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RsiRunRepository extends JpaRepository<RsiRun, UUID> {

    List<RsiRun> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
