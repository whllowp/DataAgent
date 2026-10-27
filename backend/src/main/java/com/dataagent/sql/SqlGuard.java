package com.dataagent.sql;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.ApiException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlGuard {

    private static final Set<String> READ_KEYWORDS = Set.of("SELECT", "WITH", "VALUES", "SHOW", "DESCRIBE", "DESC");
    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*([A-Za-z]+)");
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|UPSERT|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|CALL|COPY|VACUUM|ANALYZE|REINDEX|CLUSTER|LOCK|SET|RESET|COMMIT|ROLLBACK|SAVEPOINT|RELEASE|BEGIN|START|EXEC|EXECUTE|INTO|FOR\\s+UPDATE|FOR\\s+SHARE|PG_SLEEP|DBMS_LOCK|SLEEP)\\b");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern DOLLAR_QUOTED = Pattern.compile("\\$\\$.*?\\$\\$", Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXPLAIN_PREFIX = Pattern.compile("^\\s*EXPLAIN\\s*(\\([^)]*\\))?\\s*", Pattern.CASE_INSENSITIVE);

    private final DataAgentProperties properties;

    public SqlGuard(DataAgentProperties properties) {
        this.properties = properties;
    }

    public GuardedSql guardForExecution(String rawSql) {
        String sql = normalize(rawSql);
        String scanned = scanSafe(sql);
        String first = firstWord(scanned);
        if (!READ_KEYWORDS.contains(first)) {
            throw ApiException.forbidden("DataAgent 默认只允许只读查询（SELECT / WITH / VALUES / SHOW / DESCRIBE），拒绝执行: " + first);
        }
        assertNoDangerousKeywords(scanned, sql);
        return new GuardedSql(sql, first, fingerprint(sql));
    }

    public GuardedSql guardForExplain(String rawSql) {
        String sql = normalize(rawSql);
        String withoutExplain = EXPLAIN_PREFIX.matcher(sql).replaceFirst("").trim();
        if (withoutExplain.isBlank()) {
            throw ApiException.badRequest("EXPLAIN 后缺少 SQL");
        }
        String scanned = scanSafe(withoutExplain);
        if (scanned.toUpperCase(Locale.ROOT).contains("ANALYZE")) {
            throw ApiException.forbidden("为避免真实执行，DataAgent 禁止 EXPLAIN ANALYZE；请使用普通 EXPLAIN");
        }
        String first = firstWord(scanned);
        if (!Set.of("SELECT", "WITH", "VALUES").contains(first)) {
            throw ApiException.forbidden("执行计划仅支持 SELECT / WITH / VALUES 查询");
        }
        assertNoDangerousKeywords(scanned, withoutExplain);
        return new GuardedSql(withoutExplain, first, fingerprint(withoutExplain));
    }

    public String normalize(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw ApiException.badRequest("SQL 不能为空");
        }
        if (rawSql.length() > properties.getQuery().getMaxSqlLength()) {
            throw ApiException.badRequest("SQL 长度超过限制");
        }
        String sql = rawSql.trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.isBlank()) {
            throw ApiException.badRequest("SQL 不能为空");
        }
        if (containsStatementSeparator(sql)) {
            throw ApiException.forbidden("一次只能执行一条 SQL，禁止多语句执行");
        }
        return sql;
    }

    public String fingerprint(String sql) {
        String normalized = scanSafe(sql).toLowerCase(Locale.ROOT)
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\b", "?")
                .replaceAll("\\s+", " ")
                .trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.substring(0, 32);
        } catch (Exception e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    /**
     * Replaces comments and literals so keyword checks cannot be fooled by quoted text.
     */
    public String scanSafe(String sql) {
        String noBlockComments = removeBlockComments(sql);
        String noLineComments = removeLineComments(noBlockComments);
        String noDollar = DOLLAR_QUOTED.matcher(noLineComments).replaceAll("''");
        String noStrings = STRING_LITERAL.matcher(noDollar).replaceAll("''");
        return WHITESPACE.matcher(noStrings).replaceAll(" ").trim();
    }

    private void assertNoDangerousKeywords(String scanned, String original) {
        Matcher matcher = DANGEROUS.matcher(scanned.toUpperCase(Locale.ROOT));
        if (matcher.find()) {
            throw ApiException.forbidden("SQL 包含非只读或高风险关键字: " + matcher.group(1)
                    + "。如需执行变更，请走独立审批通道。");
        }
        String upper = scanned.toUpperCase(Locale.ROOT);
        if (upper.contains(" SELECT ") && upper.contains(" INTO ")) {
            throw ApiException.forbidden("禁止 SELECT ... INTO 形式");
        }
    }

    private String firstWord(String sql) {
        Matcher matcher = FIRST_WORD.matcher(sql);
        if (!matcher.find()) {
            throw ApiException.badRequest("无法识别 SQL 类型");
        }
        return matcher.group(1).toUpperCase(Locale.ROOT);
    }

    private boolean containsStatementSeparator(String sql) {
        boolean single = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (single) {
                if (c == '\'' && next == '\'') {
                    i++;
                } else if (c == '\'') {
                    single = false;
                }
                continue;
            }
            if (doubleQuote) {
                if (c == '"' && next == '"') {
                    i++;
                } else if (c == '"') {
                    doubleQuote = false;
                }
                continue;
            }
            if (backtick) {
                if (c == '`') {
                    backtick = false;
                }
                continue;
            }
            if (c == '-' && next == '-') {
                lineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                blockComment = true;
                i++;
            } else if (c == '\'') {
                single = true;
            } else if (c == '"') {
                doubleQuote = true;
            } else if (c == '`') {
                backtick = true;
            } else if (c == ';') {
                return !sql.substring(i + 1).isBlank();
            }
        }
        return false;
    }

    private String removeBlockComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean single = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (!single && !doubleQuote && !backtick && c == '/' && next == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    break;
                }
                out.append(' ');
                i = end + 1;
                continue;
            }
            if (!doubleQuote && !backtick && c == '\'') {
                single = !single || (i + 1 < sql.length() && sql.charAt(i + 1) == '\'');
                if (single && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    out.append("''");
                    i++;
                    single = false;
                    continue;
                }
            } else if (!single && !backtick && c == '"') {
                doubleQuote = !doubleQuote;
            } else if (!single && !doubleQuote && c == '`') {
                backtick = !backtick;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String removeLineComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean single = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (!single && !doubleQuote && !backtick && c == '-' && next == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
                out.append('\n');
                continue;
            }
            if (!doubleQuote && !backtick && c == '\'') {
                if (single && next == '\'') {
                    out.append("''");
                    i++;
                    continue;
                }
                single = !single;
            } else if (!single && !backtick && c == '"') {
                doubleQuote = !doubleQuote;
            } else if (!single && !doubleQuote && c == '`') {
                backtick = !backtick;
            }
            out.append(c);
        }
        return out.toString();
    }

    public record GuardedSql(String sql, String statementType, String fingerprint) {
    }
}
