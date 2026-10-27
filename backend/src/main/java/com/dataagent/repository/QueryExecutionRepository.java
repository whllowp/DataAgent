package com.dataagent.repository;

import com.dataagent.domain.QueryExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QueryExecutionRepository extends JpaRepository<QueryExecution, UUID> {

    @Query("""
            select q from QueryExecution q
            where (:datasourceId is null or q.datasourceId = :datasourceId)
              and (:solved is null or q.solved = :solved)
              and (:q is null or lower(q.sqlText) like lower(concat('%', :q, '%'))
                   or lower(coalesce(q.problem, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(q.tags, '')) like lower(concat('%', :q, '%')))
            order by q.createdAt desc
            """)
    Page<QueryExecution> search(@Param("datasourceId") UUID datasourceId,
                                @Param("solved") Boolean solved,
                                @Param("q") String q,
                                Pageable pageable);

    List<QueryExecution> findTop20ByOrderByCreatedAtDesc();

    List<QueryExecution> findTop10ByOrderByDurationMsDesc();

    long countByCreatedAtAfter(Instant instant);

    long countByDurationMsGreaterThanEqual(long threshold);

    long countBySolvedTrue();

    @Query("select avg(q.durationMs) from QueryExecution q where q.status = 'SUCCESS'")
    Double averageDuration();

    @Query("select max(q.durationMs) from QueryExecution q")
    Long maxDuration();

    @Query("select q.durationMs from QueryExecution q where q.status = 'SUCCESS' order by q.durationMs asc")
    List<Long> allSuccessfulDurations();
}
