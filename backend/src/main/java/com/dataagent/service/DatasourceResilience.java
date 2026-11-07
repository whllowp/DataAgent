package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DatasourceResilience {

    private static final Logger log = LoggerFactory.getLogger(DatasourceResilience.class);
    private final DataAgentProperties properties;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public DatasourceResilience(DataAgentProperties properties) {
        this.properties = properties;
    }

    public void beforeQuery(UUID datasourceId) {
        State state = state(datasourceId);
        synchronized (state) {
            Instant now = Instant.now();
            if (state.openUntil != null && now.isBefore(state.openUntil)) {
                throw ApiException.serviceUnavailable("数据源熔断中，请稍后重试。");
            }
            if (state.slowUntil != null && now.isBefore(state.slowUntil)) {
                throw ApiException.tooManyRequests("该数据源近期慢查询触发限流，请稍后重试。");
            }
        }
    }

    public void success(UUID datasourceId, long elapsedMs) {
        State state = state(datasourceId);
        synchronized (state) {
            state.failures = 0;
            state.openUntil = null;
            if (elapsedMs >= properties.getResilience().getSlowThresholdMs()) {
                state.slowUntil = Instant.now().plusMillis(properties.getResilience().getSlowCooldownMs());
                state.slowCount++;
                log.warn("Datasource {} slow query {}ms; throttling until {}",
                        datasourceId, elapsedMs, state.slowUntil);
            }
            state.lastDurationMs = elapsedMs;
        }
    }

    public void failure(UUID datasourceId) {
        State state = state(datasourceId);
        synchronized (state) {
            state.failures++;
            if (state.failures >= properties.getResilience().getFailureThreshold()) {
                state.openUntil = Instant.now().plusMillis(properties.getResilience().getOpenMs());
                state.failures = 0;
                log.warn("Datasource {} circuit opened until {}", datasourceId, state.openUntil);
            }
        }
    }

    public List<StateView> states() {
        return states.entrySet().stream()
                .map(entry -> new StateView(entry.getKey(), entry.getValue().failures,
                        entry.getValue().openUntil, entry.getValue().slowUntil,
                        entry.getValue().slowCount, entry.getValue().lastDurationMs))
                .toList();
    }

    public static boolean isTransient(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && (state.startsWith("08") || state.equals("40001")
                        || state.equals("40P01") || state.equals("57P01"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private State state(UUID datasourceId) {
        return states.computeIfAbsent(datasourceId, ignored -> new State());
    }

    private static final class State {
        private int failures;
        private int slowCount;
        private long lastDurationMs;
        private Instant openUntil;
        private Instant slowUntil;
    }

    public record StateView(UUID datasourceId, int failures, Instant openUntil,
                            Instant slowUntil, int slowCount, long lastDurationMs) {
    }
}
