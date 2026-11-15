package com.dataagent.repository;

import com.dataagent.domain.SemanticSqlAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SemanticSqlAttributeRepository extends JpaRepository<SemanticSqlAttribute, UUID> {

    long countByTenantId(String tenantId);

    List<SemanticSqlAttribute> findByTermIdOrderByStatusAscNameAsc(UUID termId);

    List<SemanticSqlAttribute> findByTenantIdAndStatusOrderByNameAsc(String tenantId, String status);

    @Query("""
            select a from SemanticSqlAttribute a
            where a.tenantId = :tenantId
              and (:q is null or lower(a.name) like lower(concat('%', :q, '%'))
                   or lower(a.displayName) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.businessDefinition, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.synonyms, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.tags, '')) like lower(concat('%', :q, '%')))
            order by a.status asc, a.name asc
            """)
    List<SemanticSqlAttribute> search(@Param("tenantId") String tenantId, @Param("q") String q);
}
