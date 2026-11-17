package com.dataagent.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks in-flight query sessions so a watchdog can auto-kill sessions that
 * outlive their deadline, and admins can inspect/kill sessions on demand.
 */
@Service
public class QuerySessionRegistry {

    private final ConcurrentMap<UUID, ActiveSession> sessions = new ConcurrentHashMap<>();

    public void begin(UUID queryId, UUID datasourceId, String sessionId, String sqlFingerprint,
                      Instant deadline, String user, String dialect) {
        sessions.put(queryId, new ActiveSession(queryId, datasourceId, sessionId, sqlFingerprint,
                Instant.now(), deadline, user, dialect));
    }

    public void end(UUID queryId) {
        sessions.remove(queryId);
    }

    public List<ActiveSession> active() {
        return new ArrayList<>(sessions.values());
    }

    public ActiveSession find(UUID queryId) {
        return sessions.get(queryId);
    }

    public List<ActiveSession> overdue() {
        Instant now = Instant.now();
        return sessions.values().stream()
                .filter(session -> session.deadline() != null && session.deadline().isBefore(now))
                .toList();
    }

    public record ActiveSession(
            UUID queryId,
            UUID datasourceId,
            String sessionId,
            String sqlFingerprint,
            Instant startedAt,
            Instant deadline,
            String user,
            String dialect) {
    }
}
