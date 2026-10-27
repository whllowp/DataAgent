package com.dataagent.sql;

import com.dataagent.web.dto.OptimizeDtos;
import com.dataagent.web.dto.SqlDtos;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlAnalyzer {

    private static final Pattern SELECT_STAR = Pattern.compile("\\bSELECT\\s+(?:[A-Za-z0-9_.]+\\s*,\\s*)*\\*", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_LIKE = Pattern.compile("\\bLIKE\\s+'%", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUNCTION_ON_COLUMN = Pattern.compile(
            "\\bWHERE\\b[\\s\\S]*?\\b(LOWER|UPPER|SUBSTR|SUBSTRING|TRIM|DATE|CAST|COALESCE|IFNULL|TO_CHAR|YEAR|MONTH|DAY)\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OR_PREDICATE = Pattern.compile("\\bWHERE\\b[\\s\\S]*?\\bOR\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_IN = Pattern.compile("\\bNOT\\s+IN\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT = Pattern.compile("\\b(LIMIT|FETCH\\s+FIRST|TOP\\s+\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISTINCT = Pattern.compile("\\bSELECT\\s+DISTINCT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNION = Pattern.compile("\\bUNION\\b(?!\\s+ALL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OLD_JOIN = Pattern.compile("\\bFROM\\s+[A-Za-z0-9_.]+\\s+[A-Za-z0-9_]+\\s*,\\s*[A-Za-z0-9_.]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_BY = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_IN_SUBQUERY = Pattern.compile("\\bIN\\s*\\(\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_NUMBER = Pattern.compile("\\b(=|>|<|>=|<=)\\s*'\\d+(?:\\.\\d+)?'");

    public List<OptimizeDtos.Finding> analyze(String sql, SqlDtos.PlanView plan) {
        String scanned = stripLiterals(sql);
        List<OptimizeDtos.Finding> findings = new ArrayList<>();
        add(findings, new OptimizeDtos.Finding(
                "SELECT_STAR", "MEDIUM", "SQL 写法",
                "SELECT * 读取全部列",
                "SELECT * 会读取不需要的列，宽表上增加网络与内存开销，也让覆盖索引失效。",
                "只选择真正需要的列，并明确列别名。",
                evidence(sql, SELECT_STAR)));
        if (!WHERE.matcher(scanned).find() && !isAggregateOnly(scanned)) {
            add(findings, new OptimizeDtos.Finding(
                    "NO_WHERE", "HIGH", "过滤条件",
                    "查询没有 WHERE 过滤条件",
                    "没有过滤条件会扫描整表/全分区，数据量增长后耗时线性上升。",
                    "补充时间范围、状态或主键等选择性高的过滤条件。",
                    "未匹配到 WHERE"));
        }
        add(findings, new OptimizeDtos.Finding(
                "LEADING_WILDCARD", "HIGH", "索引使用",
                "LIKE 以 % 开头",
                "前置通配符让 B-Tree 索引无法做范围定位，通常会退化为全表扫描。",
                "改写为前缀匹配、全文检索/倒排索引，或引入搜索型存储。",
                evidence(sql, LEADING_LIKE)));
        add(findings, new OptimizeDtos.Finding(
                "FUNCTION_ON_COLUMN", "HIGH", "索引使用",
                "WHERE 条件对列使用函数",
                "对列做 LOWER/UPPER/DATE/CAST 等运算会让普通索引失效。",
                "将函数移到常量侧，或建立表达式索引/生成列索引。",
                evidence(sql, FUNCTION_ON_COLUMN)));
        add(findings, new OptimizeDtos.Finding(
                "OR_PREDICATE", "MEDIUM", "过滤条件",
                "WHERE 中使用 OR",
                "跨列 OR 可能阻止索引合并，优化器可能选择全表扫描。",
                "评估改写为 UNION ALL，或为每个分支分别建立索引。",
                evidence(sql, OR_PREDICATE)));
        add(findings, new OptimizeDtos.Finding(
                "NOT_IN", "MEDIUM", "语义与索引",
                "使用 NOT IN",
                "NOT IN 遇到 NULL 时语义特殊，且常无法有效利用索引。",
                "优先改写为 NOT EXISTS 或 LEFT JOIN ... IS NULL，并确认 NULL 语义。",
                evidence(sql, NOT_IN)));
        if (ORDER_BY.matcher(scanned).find() && !LIMIT.matcher(scanned).find()) {
            add(findings, new OptimizeDtos.Finding(
                    "ORDER_BY_NO_LIMIT", "MEDIUM", "排序",
                    "ORDER BY 没有 LIMIT",
                    "对大量结果排序会消耗内存/临时空间，并把全部结果传给客户端。",
                    "增加 LIMIT，或确保 ORDER BY 能命中索引顺序。",
                    evidence(sql, ORDER_BY)));
        }
        add(findings, new OptimizeDtos.Finding(
                "DISTINCT", "LOW", "去重",
                "使用 SELECT DISTINCT",
                "DISTINCT 可能触发排序/哈希去重；如果来自一对多 JOIN，先确认重复来源。",
                "确认业务是否真的需要去重，优先修正 JOIN 粒度或使用 EXISTS。",
                evidence(sql, DISTINCT)));
        add(findings, new OptimizeDtos.Finding(
                "UNION_NOT_ALL", "LOW", "集合运算",
                "UNION 隐含去重",
                "UNION 会对两个分支排序/去重；分支本身无重复时应使用 UNION ALL。",
                "确认无重复后改为 UNION ALL，减少排序开销。",
                evidence(sql, UNION)));
        add(findings, new OptimizeDtos.Finding(
                "OLD_STYLE_JOIN", "MEDIUM", "连接写法",
                "逗号连接（隐式 JOIN）",
                "逗号连接容易漏掉连接条件形成笛卡尔积，也不利于阅读和优化。",
                "改为显式 JOIN ... ON，并检查连接字段类型与索引。",
                evidence(sql, OLD_JOIN)));
        add(findings, new OptimizeDtos.Finding(
                "IN_SUBQUERY", "LOW", "子查询",
                "IN (SELECT ...) 子查询",
                "部分优化器对 IN 子查询处理不佳，可能重复执行或产生半连接。",
                "对比 EXISTS / JOIN 改写，并用 EXPLAIN 验证。",
                evidence(sql, SELECT_IN_SUBQUERY)));
        add(findings, new OptimizeDtos.Finding(
                "IMPLICIT_CAST", "LOW", "类型",
                "字符串字面量与数字比较",
                "隐式类型转换可能让索引失效，也可能带来精度/排序问题。",
                "使用与列类型一致的字面量或显式 CAST。",
                evidence(sql, QUOTED_NUMBER)));

        if (plan != null && plan.insights() != null) {
            for (SqlDtos.PlanInsight insight : plan.insights()) {
                if ("INFO".equalsIgnoreCase(insight.level()) && "PLAN_OK".equals(insight.code())) {
                    continue;
                }
                add(findings, new OptimizeDtos.Finding(
                        "PLAN_" + insight.code(), insight.level(), "执行计划",
                        insight.title(), insight.detail(), planSuggestion(insight.code()), insight.detail()));
            }
        }
        if (findings.isEmpty()) {
            findings.add(new OptimizeDtos.Finding(
                    "NO_OBVIOUS_ISSUE", "INFO", "静态检查",
                    "未发现明显的 SQL 反模式",
                    "静态规则没有命中，但仍需结合执行计划、数据分布与真实耗时判断。",
                    "使用 EXPLAIN 与基准对比验证，不要仅凭静态规则下结论。",
                    ""));
        }
        return findings.stream().limit(16).toList();
    }

    private void add(List<OptimizeDtos.Finding> findings, OptimizeDtos.Finding candidate) {
        if (candidate.evidence() == null || candidate.evidence().isBlank()) {
            return;
        }
        boolean duplicate = findings.stream().anyMatch(f -> f.code().equals(candidate.code()));
        if (!duplicate) {
            findings.add(candidate);
        }
    }

    private String evidence(String sql, Pattern pattern) {
        Matcher matcher = pattern.matcher(sql);
        if (!matcher.find()) {
            return "";
        }
        int start = Math.max(0, matcher.start() - 40);
        int end = Math.min(sql.length(), matcher.end() + 60);
        String snippet = sql.substring(start, end).replaceAll("\\s+", " ").trim();
        return snippet.length() > 180 ? snippet.substring(0, 180) + "…" : snippet;
    }

    private String stripLiterals(String sql) {
        return sql.replaceAll("'(?:''|[^'])*'", "''");
    }

    private boolean isAggregateOnly(String scanned) {
        String upper = scanned.toUpperCase(Locale.ROOT);
        return upper.contains("COUNT(") || upper.contains("SUM(") || upper.contains("AVG(")
                || upper.contains("MIN(") || upper.contains("MAX(") || GROUP_BY.matcher(scanned).find();
    }

    private String planSuggestion(String code) {
        String normalized = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (normalized.contains("SCAN") || normalized.contains("SEQ")) {
            return "检查 WHERE/JOIN 字段的选择性与索引；必要时创建组合索引，避免在列上套函数。";
        }
        if (normalized.contains("SORT") || normalized.contains("FILESORT") || normalized.contains("TEMP")) {
            return "检查 ORDER BY/GROUP BY 字段索引，减少排序数据量或增加 LIMIT。";
        }
        if (normalized.contains("NESTED")) {
            return "确认连接字段类型一致且有索引，避免大表驱动大表。";
        }
        return "结合执行计划与数据分布验证假设，不要只改 SQL 写法。";
    }

    public String planSummary(SqlDtos.PlanView plan) {
        if (plan == null) {
            return "";
        }
        return "format=" + plan.format() + ", summary=" + plan.summary();
    }

    public List<String> guardrails() {
        return List.of(
                "只能生成只读 SQL（SELECT / WITH / VALUES），不得生成 DDL/DML。",
                "不得编造表、列、索引或执行结果；缺失信息时明确说明。",
                "性能结论必须有执行计划或基准数据支撑，区分“可能”与“已验证”。",
                "改写必须保持结果集语义一致；涉及 NULL、去重、排序时显式说明。",
                "禁止使用 EXPLAIN ANALYZE、SELECT ... INTO、存储过程或任何有副作用的语句。"
        );
    }

    public Map<String, Object> findingCounts(List<OptimizeDtos.Finding> findings) {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String severity : List.of("HIGH", "MEDIUM", "LOW", "INFO")) {
            counts.put(severity, findings.stream().filter(f -> severity.equalsIgnoreCase(f.severity())).count());
        }
        return counts;
    }
}
