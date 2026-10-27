package com.dataagent.repository;

import com.dataagent.domain.OptimizationExperiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OptimizationExperimentRepository extends JpaRepository<OptimizationExperiment, UUID> {
    List<OptimizationExperiment> findTop20ByOrderByCreatedAtDesc();
}
