package com.dataagent.repository;

import com.dataagent.domain.KnowledgeAccumulationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeAccumulationRunRepository extends JpaRepository<KnowledgeAccumulationRun, UUID> {
    List<KnowledgeAccumulationRun> findTop20ByOrderByCreatedAtDesc();
}
