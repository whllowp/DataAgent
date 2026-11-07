package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.dto.SqlDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Short-lived cache for read-only query results. Uses Redis when
 * dataagent.redis.enabled=true, otherwise an in-process map.
 */
@Service
public class QueryResultCache {

    private static final Logger log = LoggerFactory.getLogger(QueryResultCache.class);

    private final DataAgentProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public QueryResultCache(DataAgentProperties properties,
                            ObjectProvider<StringRedisTemplate> redisProvider,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
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
        if (entry != null && Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(key);
            entry = null;
        }
        if (entry == null && redisEnabled()) {
            entry = fromRedis(key);
        }
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return cached(entry.result());
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
        if (redisEnabled()) {
            toRedis(key, result);
        }
        puts.incrementAndGet();
    }

    public void invalidate(UUID datasourceId) {
        entries.keySet().removeIf(key -> key.startsWith(datasourceId.toString()));
        if (redisEnabled()) {
            try {
                StringRedisTemplate redis = redisProvider.getIfAvailable();
                if (redis != null) {
                    var keys = redis.keys(redisKey(datasourceId + ":*"));
                    if (keys != null && !keys.isEmpty()) {
                        redis.delete(keys);
                    }
                }
            } catch (Exception error) {
                log.warn("Redis cache invalidate failed: {}", error.getMessage());
            }
        }
    }

    public void clear() {
        entries.clear();
        if (redisEnabled()) {
            try {
                StringRedisTemplate redis = redisProvider.getIfAvailable();
                if (redis != null) {
                    var keys = redis.keys(redisKey("*"));
                    if (keys != null && !keys.isEmpty()) {
                        redis.delete(keys);
                    }
                }
            } catch (Exception error) {
                log.warn("Redis cache clear failed: {}", error.getMessage());
            }
        }
    }

    public CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), puts.get(), evictions.get(), entries.size(),
                properties.getQuery().getCacheMaxEntries(), properties.getQuery().getCacheTtlSeconds());
    }

    private SqlDtos.QueryResult cached(SqlDtos.QueryResult cached) {
        return new SqlDtos.QueryResult(UUID.randomUUID(), cached.status(), cached.columns(),
                cached.rows(), cached.rowCount(), cached.truncated(), 0, 0, cached.plan(),
                cached.message(), Instant.now(), true, cached.piiMaskedColumns());
    }

    private boolean redisEnabled() {
        return properties.getRedis().isEnabled() && redisProvider.getIfAvailable() != null;
    }

    private String redisKey(String suffix) {
        return properties.getRedis().getKeyPrefix() + ":q:" + suffix;
    }

    private Entry fromRedis(String key) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            String json = redis == null ? null : redis.opsForValue().get(redisKey(key));
            if (json == null) {
                return null;
            }
            SqlDtos.QueryResult result = objectMapper.readValue(json, SqlDtos.QueryResult.class);
            return new Entry(result, Instant.now().plusSeconds(properties.getQuery().getCacheTtlSeconds()));
        } catch (Exception error) {
            log.warn("Redis cache read failed: {}", error.getMessage());
            return null;
        }
    }

    private void toRedis(String key, SqlDtos.QueryResult result) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis != null) {
                redis.opsForValue().set(redisKey(key), objectMapper.writeValueAsString(result),
                        properties.getQuery().getCacheTtlSeconds(), TimeUnit.SECONDS);
            }
        } catch (Exception error) {
            log.warn("Redis cache write failed: {}", error.getMessage());
        }
    }

    private record Entry(SqlDtos.QueryResult result, Instant expiresAt) {
    }

    public record CacheStats(long hits, long misses, long puts, long evictions, int size,
                             int maxEntries, long ttlSeconds) {
    }
}
