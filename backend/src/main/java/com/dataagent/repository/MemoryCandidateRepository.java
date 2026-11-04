package com.dataagent.repository;

import com.dataagent.domain.MemoryCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemoryCandidateRepository extends JpaRepository<MemoryCandidate, UUID> {

    List<MemoryCandidate> findByStatusOrderByCreatedAtDesc(String status);

    List<MemoryCandidate> findAllByOrderByCreatedAtDesc();

    Optional<MemoryCandidate> findByContentHash(String contentHash);
}
