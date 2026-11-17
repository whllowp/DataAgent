package com.dataagent.repository;

import com.dataagent.domain.DataExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataExportRepository extends JpaRepository<DataExport, UUID> {
    List<DataExport> findTop20ByOrderByCreatedAtDesc();
}
