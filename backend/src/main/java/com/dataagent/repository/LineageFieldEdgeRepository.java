package com.dataagent.repository;

import com.dataagent.domain.LineageFieldEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LineageFieldEdgeRepository extends JpaRepository<LineageFieldEdge, UUID> {

    List<LineageFieldEdge> findByTenantId(String tenantId);

    List<LineageFieldEdge> findByTenantIdAndSourceTableAndSourceColumn(String tenantId,
                                                                       String sourceTable,
                                                                       String sourceColumn);

    List<LineageFieldEdge> findByTenantIdAndTargetTable(String tenantId, String targetTable);

    List<LineageFieldEdge> findByTenantIdAndTaskId(String tenantId, UUID taskId);

    void deleteByTenantIdAndKind(String tenantId, String kind);

    void deleteByTenantIdAndKindAndJobNameAndRunId(String tenantId, String kind,
                                                   String jobName, String runId);
}
