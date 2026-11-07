package com.dataagent.repository;

import com.dataagent.domain.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
            select event from AuditEvent event
            where (:actor is null or lower(event.actor) = lower(:actor))
              and (:action is null or upper(event.action) = upper(:action))
              and (:resourceType is null or upper(event.resourceType) = upper(:resourceType))
              and (:status is null or upper(event.status) = upper(:status))
              and (:from is null or event.createdAt >= :from)
              and (:to is null or event.createdAt <= :to)
            order by event.createdAt desc
            """)
    List<AuditEvent> search(@Param("actor") String actor,
                            @Param("action") String action,
                            @Param("resourceType") String resourceType,
                            @Param("status") String status,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);

    long countByCreatedAtAfter(Instant from);

    long countByStatusAndCreatedAtAfter(String status, Instant from);

    long countByActionAndCreatedAtAfter(String action, Instant from);

    List<AuditEvent> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);

    long deleteByCreatedAtBefore(Instant cutoff);
}
