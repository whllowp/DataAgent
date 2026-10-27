package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.Datasource;
import com.dataagent.domain.DbDialect;
import com.dataagent.domain.QueryExecution;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.sql.PlanService;
import com.dataagent.sql.ResultValueConverter;
import com.dataagent.sql.SqlGuard;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.SqlDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JdbcQueryService {

    private static final Logger log = LoggerFactory.getLogger(JdbcQueryService.class);

    private final DatasourceService datasources;
    private final SqlGuard guard;
    private final PlanService planService;
    private final ResultValueConverter valueConverter;
    private final QueryExecutionRepository executions;
    private final DataAgentProperties properties;

    public JdbcQueryService(DatasourceService datasources, SqlGuard guard, PlanService planService,
                            ResultValueConverter valueConverter, QueryExecutionRepository executions,
                            DataAgentProperties properties) {
        this.datasources = datasources;
        this.guard = guard;
        this.planService = planService;
        this.valueConverter = valueConverter;
        this.executions = executions;
        this.properties = properties;
    }

    public SqlDtos.QueryResult execute(UUID datasourceId, String rawSql, Integer maxRows,
                                       Integer timeoutSeconds, boolean includePlan,
                                       String user, String problem, String tags) {
        return execute(datasourceId, rawSql, maxRows, timeoutSeconds, includePlan, true, user, problem, tags);
    }

    public SqlDtos.QueryResult execute(UUID datasourceId, String rawSql, Integer maxRows,
                                       Integer timeoutSeconds, boolean includePlan, boolean autoPlan,
                                       String user, String problem, String tags) {
        SqlGuard.GuardedSql guarded = guard.guardForExecution(rawSql);
        Datasource datasource = datasources.getRequired(datasourceId);
        DbDialect dialect = DbDialect.from(datasource.getDialect());
        int limit = Math.min(maxRows == null ? datasource.getMaxRows() : maxRows,
                properties.getQuery().getHardMaxRows());
        limit = Math.max(1, limit);
        int timeout = Math.min(timeoutSeconds == null ? datasource.getQueryTimeoutSeconds() : timeoutSeconds,
                properties.getQuery().getHardTimeoutSeconds());
        timeout = Math.max(1, timeout);

        UUID queryId = UUID.randomUUID();
        Instant executedAt = Instant.now();
        long start = System.nanoTime();
        List<SqlDtos.ColumnMeta> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        long fetchMs = 0;
        SqlDtos.PlanView plan = null;
        String error = null;

        DataSource dataSource = datasources.dataSourceFor(datasourceId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(datasource.isReadOnly());
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(timeout);
                statement.setMaxRows(limit + 1);
                statement.setFetchSize(Math.min(limit, 500));
                try (ResultSet resultSet = statement.executeQuery(guarded.sql())) {
                    long queryDone = System.nanoTime();
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(new SqlDtos.ColumnMeta(
                                metaData.getColumnName(i),
                                metaData.getColumnLabel(i),
                                metaData.getColumnTypeName(i),
                                metaData.getColumnClassName(i),
                                metaData.getColumnType(i)));
                    }
                    int count = 0;
                    while (count < limit && resultSet.next()) {
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(valueConverter.convert(resultSet, i, metaData.getColumnType(i),
                                    properties.getQuery().getMaxCellChars()));
                        }
                        rows.add(row);
                        count++;
                    }
                    truncated = resultSet.next();
                    fetchMs = (System.nanoTime() - queryDone) / 1_000_000;
                } catch (SQLException queryError) {
                    error = friendlyError(queryError, timeout);
                }

                if (error == null && supportsPlan(guarded.statementType())) {
                    long beforePlanMs = (System.nanoTime() - start) / 1_000_000;
                    boolean shouldPlan = includePlan
                            || (autoPlan && beforePlanMs >= properties.getQuery().getAutoPlanThresholdMs());
                    if (shouldPlan) {
                        try {
                            plan = planService.explain(connection, dialect, guarded.sql());
                        } catch (Exception planError) {
                            log.debug("Plan collection failed for {}", queryId, planError);
                        }
                    }
                }
            } finally {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // read-only work; rollback is best effort
                }
            }
        } catch (SQLException e) {
            error = friendlyError(e, timeout);
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        String status = error == null ? "SUCCESS" : "ERROR";
        QueryExecution execution = new QueryExecution();
        execution.setId(queryId);
        execution.setDatasourceId(datasourceId);
        execution.setUserId(user);
        execution.setKind("QUERY");
        execution.setSqlText(guarded.sql());
        execution.setSqlFingerprint(guarded.fingerprint());
        execution.setStatus(status);
        execution.setDurationMs(elapsedMs);
        execution.setFetchMs(fetchMs);
        execution.setRowCount(rows.size());
        execution.setTruncated(truncated);
        execution.setErrorMessage(error);
        execution.setProblem(problem);
        execution.setTags(tags);
        execution.setCreatedAt(executedAt);
        if (plan != null) {
            execution.setPlanRaw(plan.raw());
            execution.setPlanFormat(plan.format());
        }
        executions.save(execution);

        if (error != null) {
            log.info("Query {} failed in {} ms: {}", queryId, elapsedMs, error);
        }

        return new SqlDtos.QueryResult(queryId, status, columns, rows, rows.size(), truncated,
                elapsedMs, fetchMs, plan, error, executedAt);
    }

    public SqlDtos.ExplainResult explain(UUID datasourceId, String rawSql, String user) {
        SqlGuard.GuardedSql guarded = guard.guardForExplain(rawSql);
        Datasource datasource = datasources.getRequired(datasourceId);
        DbDialect dialect = DbDialect.from(datasource.getDialect());
        UUID queryId = UUID.randomUUID();
        Instant executedAt = Instant.now();
        long start = System.nanoTime();
        SqlDtos.PlanView plan;
        try (Connection connection = datasources.dataSourceFor(datasourceId).getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                plan = planService.explain(connection, dialect, guarded.sql());
            } finally {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // best effort
                }
            }
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            QueryExecution execution = new QueryExecution();
            execution.setId(queryId);
            execution.setDatasourceId(datasourceId);
            execution.setUserId(user);
            execution.setKind("EXPLAIN");
            execution.setSqlText(guarded.sql());
            execution.setSqlFingerprint(guarded.fingerprint());
            execution.setStatus("ERROR");
            execution.setDurationMs(elapsed);
            execution.setErrorMessage(e.getMessage());
            execution.setCreatedAt(executedAt);
            executions.save(execution);
            throw ApiException.badRequest("获取执行计划失败: " + e.getMessage());
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        QueryExecution execution = new QueryExecution();
        execution.setId(queryId);
        execution.setDatasourceId(datasourceId);
        execution.setUserId(user);
        execution.setKind("EXPLAIN");
        execution.setSqlText(guarded.sql());
        execution.setSqlFingerprint(guarded.fingerprint());
        execution.setStatus("SUCCESS");
        execution.setDurationMs(elapsed);
        execution.setPlanRaw(plan.raw());
        execution.setPlanFormat(plan.format());
        execution.setCreatedAt(executedAt);
        executions.save(execution);
        return new SqlDtos.ExplainResult(queryId, plan);
    }

    public SqlDtos.ValidateResult validate(String rawSql) {
        SqlGuard.GuardedSql guarded = guard.guardForExecution(rawSql);
        return new SqlDtos.ValidateResult(true, "SQL 通过只读校验", guarded.statementType());
    }

    private boolean supportsPlan(String statementType) {
        return "SELECT".equals(statementType) || "WITH".equals(statementType) || "VALUES".equals(statementType);
    }

    private String friendlyError(SQLException error, int timeoutSeconds) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String state = error.getSQLState();
        if ("57014".equals(state) || message.toLowerCase().contains("timeout")) {
            return "查询超时（>" + timeoutSeconds + "s），已取消。建议缩小时间范围或补充过滤条件。";
        }
        if (message.length() > 2000) {
            message = message.substring(0, 2000) + "…";
        }
        return "SQL 执行失败: " + message;
    }
}
