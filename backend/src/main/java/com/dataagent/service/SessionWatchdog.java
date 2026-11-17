package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.DbDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically kills query sessions whose deadline has passed, enforcing the
 * server-side execution-timeout contract independently of the JDBC driver.
 */
@Component
public class SessionWatchdog {

    private static final Logger log = LoggerFactory.getLogger(SessionWatchdog.class);

    private final QuerySessionRegistry registry;
    private final QueryKiller killer;
    private final DataAgentProperties properties;

    public SessionWatchdog(QuerySessionRegistry registry, QueryKiller killer, DataAgentProperties properties) {
        this.registry = registry;
        this.killer = killer;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${dataagent.query.session-watchdog-ms:5000}")
    public void sweep() {
        if (!properties.getQuery().isKillEnabled()) {
            return;
        }
        for (QuerySessionRegistry.ActiveSession session : registry.overdue()) {
            DbDialect dialect = parseDialect(session.dialect());
            boolean killed = killer.kill(session.datasourceId(), session.sessionId(), dialect);
            registry.end(session.queryId());
            log.warn("Session {} (datasource {}) exceeded deadline and was {}",
                    session.queryId(), session.datasourceId(), killed ? "killed" : "released");
        }
    }

    private DbDialect parseDialect(String dialect) {
        try {
            return DbDialect.from(dialect);
        } catch (IllegalArgumentException error) {
            return DbDialect.H2;
        }
    }
}
