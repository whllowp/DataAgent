package com.dataagent.repository;

import com.dataagent.domain.SqlAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SqlAssetRepository extends JpaRepository<SqlAsset, UUID> {

    Optional<SqlAsset> findByFingerprint(String fingerprint);

    @Query("""
            select a from SqlAsset a
            where (:datasourceId is null or a.datasourceId = :datasourceId)
              and (:domain is null or lower(coalesce(a.domain, '')) = lower(:domain))
              and (:q is null or lower(a.sqlText) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.businessProblem, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.tags, '')) like lower(concat('%', :q, '%')))
            order by a.lastSeen desc
            """)
    Page<SqlAsset> search(@Param("datasourceId") UUID datasourceId,
                          @Param("domain") String domain,
                          @Param("q") String q,
                          Pageable pageable);
}
