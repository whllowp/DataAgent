package com.dataagent.repository;

import com.dataagent.domain.SemanticJoin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemanticJoinRepository extends JpaRepository<SemanticJoin, UUID> {

    List<SemanticJoin> findByTenantIdOrderByConfidenceDesc(String tenantId);

    List<SemanticJoin> findByFromTermIdOrToTermIdOrderByConfidenceDesc(UUID fromTermId, UUID toTermId);

    Optional<SemanticJoin> findByTenantIdAndFromTermIdAndToTermId(String tenantId, UUID fromTermId, UUID toTermId);
}
