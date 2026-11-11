package com.dataagent.repository;

import com.dataagent.domain.RsiEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RsiEvaluationRepository extends JpaRepository<RsiEvaluation, UUID> {

    List<RsiEvaluation> findByCandidateId(UUID candidateId);
}
