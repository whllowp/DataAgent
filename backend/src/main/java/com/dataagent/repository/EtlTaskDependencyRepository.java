package com.dataagent.repository;

import com.dataagent.domain.EtlTaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EtlTaskDependencyRepository extends JpaRepository<EtlTaskDependency, UUID> {

    List<EtlTaskDependency> findByWorkflowId(UUID workflowId);

    List<EtlTaskDependency> findByWorkflowIdIn(List<UUID> workflowIds);

    void deleteByWorkflowId(UUID workflowId);
}
