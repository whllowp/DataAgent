package com.dataagent.repository;

import com.dataagent.domain.SemanticTermColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemanticTermColumnRepository extends JpaRepository<SemanticTermColumn, UUID> {

    List<SemanticTermColumn> findByTermId(UUID termId);

    List<SemanticTermColumn> findByTenantIdAndStatusOrderByConfidenceDesc(String tenantId, String status);

    List<SemanticTermColumn> findByTenantIdAndTableNameIgnoreCaseAndColumnNameIgnoreCase(String tenantId,
                                                                                         String tableName,
                                                                                         String columnName);

    Optional<SemanticTermColumn> findByTenantIdAndTermIdAndTableNameIgnoreCaseAndColumnNameIgnoreCase(
            String tenantId, UUID termId, String tableName, String columnName);

    List<SemanticTermColumn> findByTenantIdAndTableNameIgnoreCase(String tenantId, String tableName);
}
