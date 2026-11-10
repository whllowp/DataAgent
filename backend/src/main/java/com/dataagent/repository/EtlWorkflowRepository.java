package com.dataagent.repository;

import com.dataagent.domain.EtlWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EtlWorkflowRepository extends JpaRepository<EtlWorkflow, UUID> {

    List<EtlWorkflow> findAllByOrderByNameAsc();

    List<EtlWorkflow> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<EtlWorkflow> findByTenantIdAndSourceAndExternalId(String tenantId, String source, String externalId);

    List<EtlWorkflow> findBySourceOrderByNameAsc(String source);
}
