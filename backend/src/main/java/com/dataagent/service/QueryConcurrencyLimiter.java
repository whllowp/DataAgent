package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

@Service
public class QueryConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(QueryConcurrencyLimiter.class);

    private final DataAgentProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final Semaphore global;
    private final Map<UUID, Semaphore> perDatasource = new ConcurrentHashMap<>();
    private final LongAdder totalRejected = new LongAdder();
    private final LongAdder totalWaitedNanos = new LongAdder();
    private final LongAdder totalQueued = new LongAdder();

    public QueryConcurrencyLimiter(DataAgentProperties properties,
                                   ObjectProvider<StringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.redisProvider = redisProvider;
        this.global = new Semaphore(Math.max(1, properties.getQuery().getMaxConcurrentQueries()), true);
    }

    public Lease acquire(UUID datasourceId) {
        if (properties.getRedis().isEnabled()) {
            Lease redisLease = acquireRedis(datasourceId);
            if (redisLease != null) {
                return redisLease;
            }
        }
        return acquireLocal(datasourceId);
    }

    private Lease acquireLocal(UUID datasourceId) {
        long waitMs = properties.getQuery().getQueueWaitTimeoutMs();
        if (global.getQueueLength() >= properties.getQuery().getMaxQueueDepth()) {
            totalRejected.increment();
            throw ApiException.tooManyRequests("查询排队已满，请稍后重试。");
        }
        long start = System.nanoTime();
        try {
            if (!global.tryAcquire(waitMs, TimeUnit.MILLISECONDS)) {
                totalRejected.increment();
                throw ApiException.tooManyRequests("查询排队等待超时（>" + waitMs + "ms），请稍后重试。");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            totalRejected.increment();
            throw ApiException.tooManyRequests("查询排队被中断。");
        }
        totalQueued.increment();
        Semaphore datasource = perDatasource.computeIfAbsent(datasourceId,
                ignored -> new Semaphore(Math.max(1, properties.getQuery().getMaxConcurrentPerDatasource()), true));
        boolean datasourceAcquired = false;
        try {
            datasourceAcquired = datasource.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (!datasourceAcquired) {
            global.release();
            totalRejected.increment();
            throw ApiException.tooManyRequests("该数据源查询排队等待超时，请稍后重试。");
        }
        totalWaitedNanos.add(System.nanoTime() - start);
        return new Lease(() -> {
            datasource.release();
            global.release();
        });
    }

    public QueueStats queueStats() {
        long queued = Math.max(0, global.getQueueLength())
                + perDatasource.values().stream().mapToInt(Semaphore::getQueueLength).sum();
        long waitedMs = totalWaitedNanos.sum() / 1_000_000;
        long queuedCount = Math.max(1, totalQueued.sum());
        return new QueueStats(
                properties.getQuery().getMaxConcurrentQueries() - global.availablePermits(),
                (int) queued,
                properties.getQuery().getMaxQueueDepth(),
                properties.getQuery().getQueueWaitTimeoutMs(),
                totalRejected.sum(),
                waitedMs / queuedCount);
    }

    public record QueueStats(int activeCount, int queuedCount, int maxQueueDepth,
                             long queueWaitTimeoutMs, long totalRejected, long avgWaitMs) {
    }

    private Lease acquireRedis(UUID datasourceId) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return null;
            }
            String prefix = properties.getRedis().getKeyPrefix() + ":quota:";
            String globalKey = prefix + "global";
            String datasourceKey = prefix + "ds:" + datasourceId;
            long ttl = Math.max(1, properties.getRedis().getQuotaTtlSeconds());
            Long globalCount = redis.opsForValue().increment(globalKey);
            redis.expire(globalKey, ttl, TimeUnit.SECONDS);
            if (globalCount != null && globalCount > properties.getQuery().getMaxConcurrentQueries()) {
                redis.opsForValue().decrement(globalKey);
                throw ApiException.tooManyRequests("集群查询并发已达上限，请稍后重试。");
            }
            Long datasourceCount = redis.opsForValue().increment(datasourceKey);
            redis.expire(datasourceKey, ttl, TimeUnit.SECONDS);
            if (datasourceCount != null
                    && datasourceCount > properties.getQuery().getMaxConcurrentPerDatasource()) {
                redis.opsForValue().decrement(datasourceKey);
                redis.opsForValue().decrement(globalKey);
                throw ApiException.tooManyRequests("该数据源集群查询并发已达上限，请稍后重试。");
            }
            AtomicBoolean released = new AtomicBoolean();
            return new Lease(() -> {
                if (released.compareAndSet(false, true)) {
                    try {
                        redis.opsForValue().decrement(datasourceKey);
                        redis.opsForValue().decrement(globalKey);
                    } catch (Exception error) {
                        log.warn("Redis quota release failed: {}", error.getMessage());
                    }
                }
            });
        } catch (ApiException limited) {
            throw limited;
        } catch (Exception error) {
            log.warn("Redis quota unavailable, falling back to local limiter: {}", error.getMessage());
            return null;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final Runnable release;

        private Lease(Runnable release) {
            this.release = release;
        }

        @Override
        public void close() {
            release.run();
        }
    }
}
