package com.dataagent.repository;

import com.dataagent.domain.SemanticRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemanticRelationshipRepository extends JpaRepository<SemanticRelationship, UUID> {

    Optional<SemanticRelationship> findByNameIgnoreCase(String name);

    List<SemanticRelationship> findAllByOrderByUpdatedAtDesc();

    List<SemanticRelationship> findByFromEntityIdOrToEntityIdOrderByNameAsc(UUID fromEntityId, UUID toEntityId);

    @Query("""
            select r from SemanticRelationship r
            where (:q is null or lower(r.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(r.description, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(r.joinCondition, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(r.synonyms, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(r.tags, '')) like lower(concat('%', :q, '%')))
            order by r.name asc
            """)
    List<SemanticRelationship> search(@Param("q") String q);
}
