package com.dataagent.repository;

import com.dataagent.domain.SemanticEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemanticEntityRepository extends JpaRepository<SemanticEntity, UUID> {

    Optional<SemanticEntity> findByNameIgnoreCase(String name);

    List<SemanticEntity> findAllByOrderByDisplayNameAsc();

    @Query("""
            select e from SemanticEntity e
            where (:q is null or lower(e.name) like lower(concat('%', :q, '%'))
                   or lower(e.displayName) like lower(concat('%', :q, '%'))
                   or lower(coalesce(e.description, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(e.synonyms, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(e.tags, '')) like lower(concat('%', :q, '%')))
              and (:domain is null or lower(coalesce(e.domain, '')) = lower(:domain))
            order by e.displayName asc
            """)
    List<SemanticEntity> search(@Param("q") String q, @Param("domain") String domain);
}
