package com.dataagent.sql;

import com.dataagent.domain.DbDialect;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.SqlDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PlanService {

    private static final int MAX_PLAN_CHARS = 200_000;
    private final ObjectMapper objectMapper;

    public PlanService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SqlDtos.PlanView explain(Connection connection, DbDialect dialect, String guardedSql) throws Exception {
        long start = System.nanoTime();
        String raw = null;
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            statement.setMaxRows(200);
            try (ResultSet resultSet = statement.executeQuery(dialect.explainPrefix() + guardedSql)) {
                StringBuilder builder = new StringBuilder();
                int columns = resultSet.getMetaData().getColumnCount();
                while (resultSet.next()) {
                    for (int i = 1; i <= columns; i++) {
                        Object value = resultSet.getObject(i);
                        if (value != null) {
                            if (!builder.isEmpty()) {
                                builder.append('\n');
                            }
                            builder.append(value);
                        }
                    }
                }
                raw = builder.toString();
            }
        } catch (Exception e) {
            throw ApiException.badRequest("获取执行计划失败: " + e.getMessage());
        }
        long explainMs = (System.nanoTime() - start) / 1_000_000;
        return analyze(dialect, raw, explainMs);
    }

    public SqlDtos.PlanView analyze(DbDialect dialect, String raw, long explainMs) {
        String safeRaw = raw == null ? "" : raw;
        if (safeRaw.length() > MAX_PLAN_CHARS) {
            safeRaw = safeRaw.substring(0, MAX_PLAN_CHARS) + "\n… plan truncated";
        }
        List<SqlDtos.PlanInsight> insights = new ArrayList<>();
        String summary;
        try {
            summary = switch (dialect) {
                case POSTGRESQL -> summarizePostgres(safeRaw, insights);
                case MYSQL -> summarizeMySql(safeRaw, insights);
                case H2 -> summarizeH2(safeRaw, insights);
            };
        } catch (Exception e) {
            summary = firstLine(safeRaw);
            insights.add(new SqlDtos.PlanInsight("INFO", "PLAN_PARSE", "计划解析降级",
                    "无法结构化解析执行计划，已保留原始计划。原因: " + e.getMessage()));
        }
        if (insights.isEmpty()) {
            insights.add(new SqlDtos.PlanInsight("INFO", "PLAN_OK", "未发现明显计划告警",
                    "仍建议结合数据量、索引与过滤条件人工复核。"));
        }
        return new SqlDtos.PlanView(dialect.planFormat(), safeRaw, summary, insights, explainMs);
    }

    private String summarizePostgres(String raw, List<SqlDtos.PlanInsight> insights) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode plan = root.isArray() && !root.isEmpty() ? root.get(0).path("Plan") : root.path("Plan");
        if (plan.isMissingNode()) {
            return firstLine(raw);
        }
        String nodeType = plan.path("Node Type").asText("Unknown");
        double totalCost = plan.path("Total Cost").asDouble(0);
        long planRows = plan.path("Plan Rows").asLong(0);
        String relation = plan.path("Relation Name").asText("");
        StringBuilder summary = new StringBuilder();
        summary.append("Root: ").append(nodeType);
        if (!relation.isBlank()) {
            summary.append(" on ").append(relation);
        }
        summary.append(" | Total Cost: ").append(formatDouble(totalCost))
                .append(" | Estimated Rows: ").append(planRows);
        walkPostgres(plan, insights);
        if (totalCost > 1000) {
            insights.add(new SqlDtos.PlanInsight("MEDIUM", "PG_HIGH_COST", "优化器估算成本较高",
                    "Total Cost=" + formatDouble(totalCost) + "，建议检查过滤条件与索引。")); 
        }
        return summary.toString();
    }

    private void walkPostgres(JsonNode node, List<SqlDtos.PlanInsight> insights) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        String type = node.path("Node Type").asText("");
        String relation = node.path("Relation Name").asText("");
        long rows = node.path("Plan Rows").asLong(0);
        double cost = node.path("Total Cost").asDouble(0);
        if (type.contains("Seq Scan") || type.contains("Full Scan")) {
            insights.add(new SqlDtos.PlanInsight("HIGH", "PG_SEQ_SCAN", "全表扫描",
                    "节点 " + type + (relation.isBlank() ? "" : " on " + relation)
                            + "，估算 " + rows + " 行，成本 " + formatDouble(cost) + "。"));
        } else if (type.contains("Nested Loop") && rows > 10_000) {
            insights.add(new SqlDtos.PlanInsight("MEDIUM", "PG_NESTED_LOOP", "大结果集 Nested Loop",
                    "Nested Loop 估算 " + rows + " 行，连接字段缺失索引时可能放大扫描量。"));
        } else if (type.contains("Sort") && !type.contains("Sort Key")) {
            insights.add(new SqlDtos.PlanInsight("LOW", "PG_SORT", "排序节点",
                    "执行计划包含排序，检查 ORDER BY / GROUP BY / DISTINCT 是否可命中索引。"));
        } else if (type.contains("Hash Join")) {
            insights.add(new SqlDtos.PlanInsight("INFO", "PG_HASH_JOIN", "Hash Join",
                    "Hash Join 通常需要较多内存，关注 work_mem 与扫描行数。"));
        }
        JsonNode children = node.path("Plans");
        if (children.isArray()) {
            for (JsonNode child : children) {
                walkPostgres(child, insights);
            }
        }
    }

    private String summarizeMySql(String raw, List<SqlDtos.PlanInsight> insights) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode queryBlock = root.path("query_block");
        if (queryBlock.isMissingNode()) {
            return firstLine(raw);
        }
        double cost = queryBlock.path("cost_info").path("query_cost").asDouble(0);
        List<String> tables = new ArrayList<>();
        walkMySql(queryBlock, insights, tables);
        return "MySQL query_block | cost=" + formatDouble(cost) + " | tables=" + String.join(", ", tables);
    }

    private void walkMySql(JsonNode node, List<SqlDtos.PlanInsight> insights, List<String> tables) {
        if (node == null) {
            return;
        }
        if (node.has("table_name")) {
            String table = node.path("table_name").asText("");
            tables.add(table);
            String accessType = node.path("access_type").asText("");
            String key = node.path("key").asText("");
            long rows = node.path("rows_examined_per_scan").asLong(0);
            if ("ALL".equalsIgnoreCase(accessType)) {
                insights.add(new SqlDtos.PlanInsight("HIGH", "MYSQL_FULL_SCAN", "全表扫描: " + table,
                        "access_type=ALL，估算检查 " + rows + " 行，未使用索引。"));
            } else if (key.isBlank()) {
                insights.add(new SqlDtos.PlanInsight("MEDIUM", "MYSQL_NO_KEY", "未使用索引: " + table,
                        "access_type=" + accessType + "，key 为空，检查过滤/连接字段索引。"));
            }
            if (node.path("using_filesort").asBoolean(false)) {
                insights.add(new SqlDtos.PlanInsight("MEDIUM", "MYSQL_FILESORT", "Using filesort",
                        "排序无法利用索引，建议为 ORDER BY 字段建立合适索引。"));
            }
            if (node.path("using_temporary_table").asBoolean(false)) {
                insights.add(new SqlDtos.PlanInsight("MEDIUM", "MYSQL_TEMP_TABLE", "Using temporary",
                        "查询使用了临时表，常见于 GROUP BY / DISTINCT / UNION。"));
            }
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                walkMySql(value, insights, tables);
            } else if (value.isArray()) {
                for (JsonNode child : value) {
                    walkMySql(child, insights, tables);
                }
            }
        });
    }

    private String summarizeH2(String raw, List<SqlDtos.PlanInsight> insights) {
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("FULL SCAN") || upper.contains("TABLESCAN")) {
            insights.add(new SqlDtos.PlanInsight("HIGH", "H2_FULL_SCAN", "全表扫描",
                    "H2 执行计划包含 FULL SCAN / tableScan，建议为过滤或连接字段增加索引。"));
        }
        if (upper.contains("SORT") && !upper.contains("SORTED")) {
            insights.add(new SqlDtos.PlanInsight("MEDIUM", "H2_SORT", "排序节点",
                    "H2 计划包含排序，检查 ORDER BY 字段索引与结果集大小。"));
        }
        List<String> access = new ArrayList<>();
        var matcher = H2_ACCESS.matcher(raw);
        while (matcher.find()) {
            String relation = matcher.group(1);
            String suffix = matcher.group(2);
            if (suffix != null && suffix.toLowerCase(Locale.ROOT).contains("tablescan")) {
                access.add(relation + " [full scan]");
            } else if (suffix != null && suffix.trim().startsWith(":")) {
                access.add("index " + relation);
            } else if (suffix == null || suffix.isBlank()) {
                access.add(relation);
            }
        }
        if (!access.isEmpty()) {
            return "H2 plan | " + String.join("; ", access);
        }
        return firstLine(raw);
    }

    private static final java.util.regex.Pattern H2_ACCESS = java.util.regex.Pattern.compile(
            "/\\*\\s*[A-Za-z0-9_$]+\\.([A-Za-z0-9_$]+)([^*]*)\\*/");

    private String firstLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return "执行计划为空";
        }
        String trimmed = raw.trim();
        int newline = trimmed.indexOf('\n');
        String line = newline < 0 ? trimmed : trimmed.substring(0, newline);
        return line.length() > 500 ? line.substring(0, 500) + "…" : line;
    }

    private static String formatDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
