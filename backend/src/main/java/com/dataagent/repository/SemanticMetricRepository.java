package com.dataagent.repository;

import com.dataagent.domain.SemanticMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemanticMetricRepository extends JpaRepository<SemanticMetric, UUID> {

    Optional<SemanticMetric> findByNameIgnoreCase(String name);

    List<SemanticMetric> findAllByOrderByDisplayNameAsc();

    List<SemanticMetric> findByEntityIdOrderByDisplayNameAsc(UUID entityId);

    @Query("""
            select m from SemanticMetric m
            where (:q is null or lower(m.name) like lower(concat('%', :q, '%'))
                   or lower(m.displayName) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.businessDefinition, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.expression, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.synonyms, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.tags, '')) like lower(concat('%', :q, '%')))
            order by m.displayName asc
            """)
    List<SemanticMetric> search(@Param("q") String q);
}
