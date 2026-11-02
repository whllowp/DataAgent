package com.dataagent.repository;

import com.dataagent.domain.VerifiedQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VerifiedQueryRepository extends JpaRepository<VerifiedQuery, UUID> {

    List<VerifiedQuery> findAllByOrderByUpdatedAtDesc();

    List<VerifiedQuery> findByStatusOrderByUpdatedAtDesc(String status);

    @Query("""
            select v from VerifiedQuery v
            where (:q is null or lower(v.question) like lower(concat('%', :q, '%'))
                   or lower(v.sqlText) like lower(concat('%', :q, '%'))
                   or lower(coalesce(v.tags, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(v.notes, '')) like lower(concat('%', :q, '%')))
            order by v.useCount desc, v.updatedAt desc
            """)
    List<VerifiedQuery> search(@Param("q") String q);

    @Query("""
            select v from VerifiedQuery v
            where (:metricId is null or v.metricId = :metricId)
              and (:entityId is null or v.entityId = :entityId)
            order by v.useCount desc, v.updatedAt desc
            """)
    List<VerifiedQuery> findByMetricOrEntity(@Param("metricId") UUID metricId,
                                             @Param("entityId") UUID entityId);
}
