package com.dataagent.repository;

import com.dataagent.domain.SemanticSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SemanticSyncRunRepository extends JpaRepository<SemanticSyncRun, UUID> {

    List<SemanticSyncRun> findTop20ByOrderByStartedAtDesc();

    List<SemanticSyncRun> findTop10ByOrderByStartedAtDesc();
}
