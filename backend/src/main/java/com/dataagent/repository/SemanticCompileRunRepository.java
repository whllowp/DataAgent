package com.dataagent.repository;

import com.dataagent.domain.SemanticCompileRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SemanticCompileRunRepository extends JpaRepository<SemanticCompileRun, UUID> {

    List<SemanticCompileRun> findTop20ByTenantIdOrderByStartedAtDesc(String tenantId);
}
