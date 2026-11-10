package com.dataagent.repository;

import com.dataagent.domain.EtlTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EtlTaskRepository extends JpaRepository<EtlTask, UUID> {

    List<EtlTask> findByWorkflowIdOrderByOrderIndexAsc(UUID workflowId);

    List<EtlTask> findByWorkflowIdIn(List<UUID> workflowIds);

    void deleteByWorkflowId(UUID workflowId);
}
