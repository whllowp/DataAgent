package com.dataagent.web;

import com.dataagent.domain.DbDialect;
import com.dataagent.service.QueryConcurrencyLimiter;
import com.dataagent.service.QueryKiller;
import com.dataagent.service.QuerySessionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQL governance observability: active sessions, session kill, and queue stats.
 */
@RestController
@RequestMapping("/api/ops")
public class SqlGovernanceController {

    private final QuerySessionRegistry registry;
    private final QueryKiller killer;
    private final QueryConcurrencyLimiter limiter;

    public SqlGovernanceController(QuerySessionRegistry registry, QueryKiller killer,
                                   QueryConcurrencyLimiter limiter) {
        this.registry = registry;
        this.killer = killer;
        this.limiter = limiter;
    }

    @GetMapping("/sessions")
    public List<QuerySessionRegistry.ActiveSession> sessions() {
        return registry.active();
    }

    @PostMapping("/sessions/{queryId}/kill")
    public Map<String, Object> kill(@PathVariable UUID queryId) {
        QuerySessionRegistry.ActiveSession session = registry.find(queryId);
        if (session == null) {
            return Map.of("killed", false, "message", "会话不存在或已结束");
        }
        boolean killed = killer.kill(session.datasourceId(), session.sessionId(), DbDialect.from(session.dialect()));
        registry.end(queryId);
        return Map.of("killed", killed, "sessionId", String.valueOf(session.sessionId()),
                "queryId", queryId.toString());
    }

    @GetMapping("/queue")
    public QueryConcurrencyLimiter.QueueStats queue() {
        return limiter.queueStats();
    }
}
