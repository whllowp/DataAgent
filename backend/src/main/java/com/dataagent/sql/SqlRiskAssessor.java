package com.dataagent.sql;

import com.dataagent.config.DataAgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scores a read-only SQL for high-risk / expensive execution patterns before it
 * runs. A score at or above {@code dataagent.query.risk-block-score} blocks
 * execution unless the caller opts in via {@code allowRisky}.
 */
@Component
public class SqlRiskAssessor {

    private static final Pattern SELECT_STAR = Pattern.compile("\\bSELECT\\s+(?:[A-Za-z0-9_.]+\\s*,\\s*)*\\*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_BY = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT = Pattern.compile("\\b(LIMIT|FETCH\\s+FIRST|TOP\\s+\\d+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OLD_JOIN = Pattern.compile(
            "\\bFROM\\s+[A-Za-z0-9_.]+\\s+[A-Za-z0-9_]+\\s*,\\s*[A-Za-z0-9_.]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_LIKE = Pattern.compile("\\bLIKE\\s+'%", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUNCTION_ON_COLUMN = Pattern.compile(
            "\\bWHERE\\b[\\s\\S]*?\\b(LOWER|UPPER|SUBSTR|SUBSTRING|TRIM|DATE|CAST|COALESCE|IFNULL|TO_CHAR|YEAR|MONTH|DAY)\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OR_PREDICATE = Pattern.compile("\\bWHERE\\b[\\s\\S]*?\\bOR\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_IN = Pattern.compile("\\bNOT\\s+IN\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISTINCT = Pattern.compile("\\bSELECT\\s+DISTINCT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNION = Pattern.compile("\\bUNION\\b(?!\\s+ALL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_SUBQUERY = Pattern.compile("\\bIN\\s*\\(\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_NUMBER = Pattern.compile("\\b(=|>|<|>=|<=)\\s*'\\d+(?:\\.\\d+)?'");
    private static final Pattern OFFSET_BIG = Pattern.compile("\\bOFFSET\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private final DataAgentProperties properties;

    public SqlRiskAssessor(DataAgentProperties properties) {
        this.properties = properties;
    }

    public RiskAssessment assess(String sql) {
        String scanned = stripLiterals(sql);
        List<Risk> risks = new ArrayList<>();
        add(risks, 30, "FULL_SCAN", "HIGH", "无过滤条件",
                "没有 WHERE 会全量扫描，数据量增长后耗时线性上升。",
                WHERE.matcher(scanned).find() || isAggregateOnly(scanned) ? "" : "未匹配到 WHERE");
        add(risks, 30, "CARTESIAN_JOIN", "HIGH", "逗号连接（可能笛卡尔积）",
                "隐式逗号连接容易漏连接条件形成笛卡尔积。",
                evidence(sql, OLD_JOIN));
        add(risks, 20, "LEADING_WILDCARD", "HIGH", "LIKE 前置通配符",
                "前置 % 使索引失效，退化为全表扫描。",
                evidence(sql, LEADING_LIKE));
        add(risks, 15, "FUNCTION_ON_COLUMN", "MEDIUM", "对列使用函数",
                "WHERE 中对列做函数运算会使索引失效。",
                evidence(sql, FUNCTION_ON_COLUMN));
        add(risks, 15, "SELECT_STAR", "MEDIUM", "SELECT *",
                "读取全部列，宽表上增加网络与内存开销。",
                evidence(sql, SELECT_STAR));
        add(risks, 10, "ORDER_BY_NO_LIMIT", "MEDIUM", "ORDER BY 无 LIMIT",
                "对大量结果排序消耗内存/临时空间。",
                ORDER_BY.matcher(scanned).find() && !LIMIT.matcher(scanned).find()
                        ? evidence(sql, ORDER_BY) : "");
        add(risks, 10, "OR_PREDICATE", "MEDIUM", "WHERE 中使用 OR",
                "跨列 OR 可能阻止索引合并。",
                evidence(sql, OR_PREDICATE));
        add(risks, 10, "NOT_IN", "MEDIUM", "使用 NOT IN",
                "NOT IN 遇到 NULL 语义特殊且常无法利用索引。",
                evidence(sql, NOT_IN));
        add(risks, 10, "LARGE_OFFSET", "MEDIUM", "大 OFFSET",
                "大 OFFSET 需要跳过大量行，深度分页成本高。",
                largeOffset(scanned));
        add(risks, 5, "DISTINCT", "LOW", "SELECT DISTINCT",
                "DISTINCT 可能触发排序/哈希去重。",
                evidence(sql, DISTINCT));
        add(risks, 5, "UNION_NOT_ALL", "LOW", "UNION 隐含去重",
                "UNION 会排序/去重，分支无重复时应使用 UNION ALL。",
                evidence(sql, UNION));
        add(risks, 5, "IN_SUBQUERY", "LOW", "IN (SELECT...) 子查询",
                "部分优化器对 IN 子查询处理不佳。",
                evidence(sql, IN_SUBQUERY));
        add(risks, 5, "IMPLICIT_CAST", "LOW", "隐式类型转换",
                "字符串字面量与数字比较可能让索引失效。",
                evidence(sql, QUOTED_NUMBER));

        int score = risks.stream().mapToInt(Risk::weight).sum();
        score = Math.min(100, score);
        int blockScore = properties.getQuery().getRiskBlockScore();
        int warnScore = properties.getQuery().getRiskWarnScore();
        String level = level(score, warnScore);
        boolean blocked = score >= blockScore;
        return new RiskAssessment(score, level, blocked, risks);
    }

    private String level(int score, int warnScore) {
        if (score >= 80) {
            return "CRITICAL";
        }
        if (score >= 60) {
            return "HIGH";
        }
        if (score >= warnScore) {
            return "MEDIUM";
        }
        if (score > 0) {
            return "LOW";
        }
        return "NONE";
    }

    private String largeOffset(String scanned) {
        Matcher matcher = OFFSET_BIG.matcher(scanned);
        if (matcher.find()) {
            try {
                if (Long.parseLong(matcher.group(1)) >= 10_000) {
                    return matcher.group(0);
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return "";
    }

    private void add(List<Risk> risks, int weight, String code, String severity, String title,
                     String detail, String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return;
        }
        boolean duplicate = risks.stream().anyMatch(r -> r.code().equals(code));
        if (!duplicate) {
            risks.add(new Risk(code, severity, weight, title, detail, evidence));
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

    public record Risk(String code, String severity, int weight, String title, String detail, String evidence) {
    }

    public record RiskAssessment(int score, String level, boolean blocked, List<Risk> risks) {
    }
}
