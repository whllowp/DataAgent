package com.dataagent.repository;

import com.dataagent.domain.LineageEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LineageEdgeRepository extends JpaRepository<LineageEdge, UUID> {

    List<LineageEdge> findByTenantId(String tenantId);

    List<LineageEdge> findByTenantIdAndSourceTable(String tenantId, String sourceTable);

    List<LineageEdge> findByTenantIdAndTargetTable(String tenantId, String targetTable);

    void deleteByTenantIdAndKind(String tenantId, String kind);

    void deleteByTenantIdAndKindAndJobNameAndRunId(String tenantId, String kind,
                                                   String jobName, String runId);
}
