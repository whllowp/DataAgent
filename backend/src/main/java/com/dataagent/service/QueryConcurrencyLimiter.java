package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class QueryConcurrencyLimiter {

    private final DataAgentProperties properties;
    private final Semaphore global;
    private final Map<UUID, Semaphore> perDatasource = new ConcurrentHashMap<>();

    public QueryConcurrencyLimiter(DataAgentProperties properties) {
        this.properties = properties;
        this.global = new Semaphore(Math.max(1, properties.getQuery().getMaxConcurrentQueries()), true);
    }

    public Lease acquire(UUID datasourceId) {
        if (!global.tryAcquire()) {
            throw ApiException.tooManyRequests("查询并发已达上限，请稍后重试。");
        }
        Semaphore datasource = perDatasource.computeIfAbsent(datasourceId,
                ignored -> new Semaphore(Math.max(1, properties.getQuery().getMaxConcurrentPerDatasource()), true));
        if (!datasource.tryAcquire()) {
            global.release();
            throw ApiException.tooManyRequests("该数据源查询并发已达上限，请稍后重试。");
        }
        return new Lease(datasource, global);
    }

    public record Lease(Semaphore datasource, Semaphore global) implements AutoCloseable {
        @Override
        public void close() {
            datasource.release();
            global.release();
        }
    }
}
