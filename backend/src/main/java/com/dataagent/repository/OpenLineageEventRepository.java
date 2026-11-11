package com.dataagent.repository;

import com.dataagent.domain.OpenLineageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OpenLineageEventRepository extends JpaRepository<OpenLineageEvent, UUID> {

    List<OpenLineageEvent> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<OpenLineageEvent> findTop100ByTenantIdAndJobNameOrderByCreatedAtDesc(String tenantId, String jobName);

    List<OpenLineageEvent> findByTenantIdAndJobNameAndRunIdOrderByCreatedAtDesc(String tenantId,
                                                                               String jobName,
                                                                               String runId);
}
