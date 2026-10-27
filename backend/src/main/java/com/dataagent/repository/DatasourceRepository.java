package com.dataagent.repository;

import com.dataagent.domain.Datasource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DatasourceRepository extends JpaRepository<Datasource, UUID> {
    Optional<Datasource> findByName(String name);
}
