package com.dataagent.repository;

import com.dataagent.domain.AgentFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentFeedbackRepository extends JpaRepository<AgentFeedback, UUID> {

    List<AgentFeedback> findTop50ByOrderByCreatedAtDesc();
}
