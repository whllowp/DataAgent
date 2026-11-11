package com.dataagent.repository;

import com.dataagent.domain.RsiCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RsiCandidateRepository extends JpaRepository<RsiCandidate, UUID> {

    List<RsiCandidate> findByRunIdOrderByGenerationAscScalarScoreDesc(UUID runId);

    List<RsiCandidate> findByRunIdAndGenerationOrderByScalarScoreDesc(UUID runId, int generation);
}
