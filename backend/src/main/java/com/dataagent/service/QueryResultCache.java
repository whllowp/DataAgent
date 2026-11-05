package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.dto.SqlDtos;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Short-lived, in-process cache for read-only query results. This absorbs
 * repeated dashboard/agent reads without changing the read-only gateway.
 */
@Service
public class QueryResultCache {

    private final DataAgentProperties properties;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public QueryResultCache(DataAgentProperties properties) {
        this.properties = properties;
    }

    public String key(UUID datasourceId, String sql, int maxRows, boolean includePlan, boolean autoPlan) {
        String raw = datasourceId + "|" + maxRows + "|" + includePlan + "|" + autoPlan + "|" + sql;
        try {
            return datasourceId + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return datasourceId + ":" + Integer.toHexString(raw.hashCode());
        }
    }

    public SqlDtos.QueryResult get(String key) {
        if (!properties.getQuery().isCacheEnabled() || properties.getQuery().getCacheTtlSeconds() <= 0) {
            misses.incrementAndGet();
            return null;
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(key);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        SqlDtos.QueryResult cached = entry.result();
        return new SqlDtos.QueryResult(UUID.randomUUID(), cached.status(), cached.columns(),
                cached.rows(), cached.rowCount(), cached.truncated(), 0, 0, cached.plan(),
                cached.message(), Instant.now(), true);
    }

    public void put(String key, SqlDtos.QueryResult result) {
        if (!properties.getQuery().isCacheEnabled() || properties.getQuery().getCacheTtlSeconds() <= 0
                || !"SUCCESS".equalsIgnoreCase(result.status())) {
            return;
        }
        int max = Math.max(1, properties.getQuery().getCacheMaxEntries());
        if (entries.size() >= max) {
            entries.entrySet().removeIf(item -> Instant.now().isAfter(item.getValue().expiresAt()));
            if (entries.size() >= max) {
                entries.keySet().stream().findFirst().ifPresent(entries::remove);
                evictions.incrementAndGet();
            }
        }
        entries.put(key, new Entry(result,
                Instant.now().plusSeconds(properties.getQuery().getCacheTtlSeconds())));
        puts.incrementAndGet();
    }

    public void invalidate(UUID datasourceId) {
        entries.keySet().removeIf(key -> key.startsWith(datasourceId.toString()));
    }

    public void clear() {
        entries.clear();
    }

    public CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), puts.get(), evictions.get(), entries.size(),
                properties.getQuery().getCacheMaxEntries(), properties.getQuery().getCacheTtlSeconds());
    }

    private record Entry(SqlDtos.QueryResult result, Instant expiresAt) {
    }

    public record CacheStats(long hits, long misses, long puts, long evictions, int size,
                             int maxEntries, long ttlSeconds) {
    }
}
