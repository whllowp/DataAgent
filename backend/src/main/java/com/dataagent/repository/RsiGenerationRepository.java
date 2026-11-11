package com.dataagent.repository;

import com.dataagent.domain.RsiGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RsiGenerationRepository extends JpaRepository<RsiGeneration, UUID> {

    List<RsiGeneration> findByRunIdOrderByGenerationNumberAsc(UUID runId);
}
