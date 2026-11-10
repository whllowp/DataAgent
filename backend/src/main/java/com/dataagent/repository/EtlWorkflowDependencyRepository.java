package com.dataagent.repository;

import com.dataagent.domain.EtlWorkflowDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EtlWorkflowDependencyRepository extends JpaRepository<EtlWorkflowDependency, UUID> {

    List<EtlWorkflowDependency> findByUpstreamWorkflowIdOrDownstreamWorkflowId(UUID upstream, UUID downstream);

    List<EtlWorkflowDependency> findAll();
}
