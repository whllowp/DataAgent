package com.dataagent.repository;

import com.dataagent.domain.KnowledgeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, UUID> {

    @Query("""
            select k from KnowledgeEntry k
            where (:q is null or lower(k.title) like lower(concat('%', :q, '%'))
                   or lower(coalesce(k.problem, '')) like lower(concat('%', :q, '%'))
                   or lower(k.sqlText) like lower(concat('%', :q, '%'))
                   or lower(coalesce(k.tags, '')) like lower(concat('%', :q, '%')))
              and (:dialect is null or k.dialect = :dialect)
              and (:tag is null or lower(coalesce(k.tags, '')) like lower(concat('%', :tag, '%')))
            order by k.updatedAt desc
            """)
    Page<KnowledgeEntry> search(@Param("q") String q,
                                @Param("dialect") String dialect,
                                @Param("tag") String tag,
                                Pageable pageable);
}
