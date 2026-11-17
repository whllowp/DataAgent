package com.dataagent.service;

import com.dataagent.domain.DbDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Dialect-aware session termination. A timed-out or manually killed session is
 * terminated server-side (not just abandoned by the JDBC driver), so the query
 * stops consuming database resources.
 */
@Service
public class QueryKiller {

    private static final Logger log = LoggerFactory.getLogger(QueryKiller.class);

    private final DatasourceService datasources;

    public QueryKiller(DatasourceService datasources) {
        this.datasources = datasources;
    }

    /** Fetch the DB-side session/connection id for the current connection. */
    public String sessionIdOf(Connection connection, DbDialect dialect) {
        String sql = switch (dialect) {
            case POSTGRESQL -> "SELECT pg_backend_pid()";
            case MYSQL -> "SELECT CONNECTION_ID()";
            case H2 -> "SELECT SESSION_ID()";
        };
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        } catch (SQLException error) {
            log.debug("Failed to read session id ({}): {}", dialect, error.getMessage());
        }
        return null;
    }

    /** Terminate a session on a fresh connection. Best effort; never throws. */
    public boolean kill(UUID datasourceId, String sessionId, DbDialect dialect) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try {
            DataSource dataSource = datasources.dataSourceForUnscoped(datasourceId);
            try (Connection connection = dataSource.getConnection()) {
                switch (dialect) {
                    case POSTGRESQL -> {
                        try (PreparedStatement statement =
                                     connection.prepareStatement("SELECT pg_terminate_backend(?)")) {
                            statement.setInt(1, Integer.parseInt(sessionId));
                            statement.execute();
                        }
                    }
                    case MYSQL -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("KILL " + sessionId);
                        }
                    }
                    case H2 -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("CANCEL_SESSION " + sessionId);
                        }
                    }
                }
            }
            log.info("Killed session {} on datasource {} ({})", sessionId, datasourceId, dialect);
            return true;
        } catch (Exception error) {
            log.warn("Failed to kill session {} on datasource {} ({}): {}",
                    sessionId, datasourceId, dialect, error.getMessage());
            return false;
        }
    }
}
