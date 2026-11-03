package com.dataagent.repository;

import com.dataagent.domain.AgentMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, UUID> {

    @Query("""
            select m from AgentMemory m
            where lower(m.scope) = lower(:scope)
              and ((:scopeKey is null and m.scopeKey is null) or lower(m.scopeKey) = lower(:scopeKey))
              and lower(m.memoryKey) = lower(:key)
            """)
    Optional<AgentMemory> findExact(@Param("scope") String scope,
                                    @Param("scopeKey") String scopeKey,
                                    @Param("key") String key);

    @Query("""
            select m from AgentMemory m
            where (:q is null or lower(m.memoryKey) like lower(concat('%', :q, '%'))
                   or lower(m.content) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.tags, '')) like lower(concat('%', :q, '%')))
              and (:scope is null or lower(m.scope) = lower(:scope))
              and (:kind is null or lower(m.kind) = lower(:kind))
              and (:status is null or lower(m.status) = lower(:status))
            order by m.importance desc, m.updatedAt desc
            """)
    List<AgentMemory> search(@Param("q") String q,
                             @Param("scope") String scope,
                             @Param("kind") String kind,
                             @Param("status") String status);

    @Query("""
            select m from AgentMemory m
            where m.status = 'ACTIVE'
              and (m.expiresAt is null or m.expiresAt > :now)
              and (m.scope = 'GLOBAL'
                   or (m.scope = 'DOMAIN' and lower(m.scopeKey) = lower(:domainKey))
                   or (m.scope = 'DATASOURCE' and lower(m.scopeKey) = lower(:datasourceKey))
                   or (m.scope = 'USER' and lower(m.scopeKey) = lower(:userKey)))
            order by m.importance desc, m.confidence desc, m.updatedAt desc
            """)
    List<AgentMemory> findContextCandidates(@Param("domainKey") String domainKey,
                                            @Param("datasourceKey") String datasourceKey,
                                            @Param("userKey") String userKey,
                                            @Param("now") Instant now);

    List<AgentMemory> findByStatusAndExpiresAtBefore(String status, Instant now);
}
