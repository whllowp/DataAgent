package com.dataagent.repository;

import com.dataagent.domain.SqlBenchmarkCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SqlBenchmarkCaseRepository extends JpaRepository<SqlBenchmarkCase, UUID> {

    Optional<SqlBenchmarkCase> findByNameIgnoreCase(String name);

    List<SqlBenchmarkCase> findAllByOrderByUpdatedAtDesc();

    List<SqlBenchmarkCase> findByStatusOrderByNameAsc(String status);
}
