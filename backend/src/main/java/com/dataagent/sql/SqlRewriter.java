package com.dataagent.sql;

import com.dataagent.config.DataAgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Safe, semantics-preserving rewrites for read-only SQL. Only changes that
 * cannot alter the meaning of the result set are applied automatically;
 * changes that affect dedup / ordering semantics are returned as suggestions.
 */
@Component
public class SqlRewriter {

    private static final Pattern LIMIT = Pattern.compile(
            "\\b(LIMIT\\s+\\d+|FETCH\\s+FIRST|TOP\\s+\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM = Pattern.compile("\\bFROM\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_BY = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNION_NOT_ALL = Pattern.compile("\\bUNION\\b(?!\\s+ALL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEMICOLON = Pattern.compile(";+\\s*$");

    private final DataAgentProperties properties;

    public SqlRewriter(DataAgentProperties properties) {
        this.properties = properties;
    }

    public RewriteResult rewrite(String sql) {
        String normalized = SEMICOLON.matcher(sql.trim()).replaceFirst("");
        int hardMaxRows = properties.getQuery().getHardMaxRows();
        List<Rewrite> applied = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        String rewritten = normalized;
        boolean hasFrom = FROM.matcher(rewritten).find();
        boolean hasLimit = LIMIT.matcher(rewritten).find();
        boolean hasOrderBy = ORDER_BY.matcher(rewritten).find();
        boolean hasWhere = WHERE.matcher(rewritten).find();
        boolean aggregateOnly = isAggregateOnly(rewritten);

        // 1. ORDER BY without LIMIT -> append LIMIT (caps sorted result size).
        if (hasFrom && hasOrderBy && !hasLimit) {
            rewritten = rewritten + " LIMIT " + hardMaxRows;
            applied.add(new Rewrite("ADD_LIMIT", "排序结果未限制行数，追加 LIMIT " + hardMaxRows + "。",
                    normalized, rewritten));
            hasLimit = true;
        }

        // 2. Full scan without WHERE/LIMIT -> append LIMIT.
        if (hasFrom && !hasWhere && !hasLimit && !aggregateOnly) {
            rewritten = rewritten + " LIMIT " + hardMaxRows;
            applied.add(new Rewrite("CAP_FULL_SCAN", "无过滤条件且无 LIMIT，追加 LIMIT " + hardMaxRows + " 封顶。",
                    normalized, rewritten));
        }

        // 3. UNION -> UNION ALL is a semantic change (dedup removed); suggest only.
        if (UNION_NOT_ALL.matcher(rewritten).find()) {
            suggestions.add("UNION 隐含去重；若各分支结果本身无重复，改写为 UNION ALL 可省去排序/去重。");
        }

        if (applied.isEmpty()) {
            risks.add("未发现可安全自动改写的反模式；如需优化请参考 analyze/compare 或人工改写。");
        } else {
            risks.add("自动改写仅追加 LIMIT（不改变行语义），请在结果集中验证列与行序未受影响。");
        }

        return new RewriteResult(rewritten, applied, suggestions, risks);
    }

    private boolean isAggregateOnly(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        return upper.contains("COUNT(") || upper.contains("SUM(") || upper.contains("AVG(")
                || upper.contains("MIN(") || upper.contains("MAX(") || GROUP_BY.matcher(sql).find();
    }

    public record Rewrite(String type, String detail, String before, String after) {
    }

    public record RewriteResult(String rewrittenSql, List<Rewrite> applied, List<String> suggestions,
                                List<String> risks) {
    }
}
