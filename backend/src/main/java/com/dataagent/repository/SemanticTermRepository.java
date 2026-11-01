package com.dataagent.repository;

import com.dataagent.domain.SemanticTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemanticTermRepository extends JpaRepository<SemanticTerm, UUID> {

    Optional<SemanticTerm> findByTermIgnoreCase(String term);

    List<SemanticTerm> findAllByOrderByTermAsc();

    @Query("""
            select t from SemanticTerm t
            where (:q is null or lower(t.term) like lower(concat('%', :q, '%'))
                   or lower(coalesce(t.definition, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(t.synonyms, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(t.tags, '')) like lower(concat('%', :q, '%')))
            order by t.term asc
            """)
    List<SemanticTerm> search(@Param("q") String q);
}
