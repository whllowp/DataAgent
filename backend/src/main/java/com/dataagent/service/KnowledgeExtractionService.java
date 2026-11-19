package com.dataagent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schema-aware extraction of durable knowledge from an executed SQL statement.
 *
 * <p>The extractor is deliberately deterministic: the same SQL, business problem
 * and dialect always produce the same facts. That makes it usable both for the
 * production accumulation path and for a replayable accuracy benchmark. The
 * expensive surface parse is cached by SQL text; the ten-round extraction lab
 * uses the same production extractor, not a separate toy implementation.</p>
 */
@Service
public class KnowledgeExtractionService {

    private static final int CACHE_LIMIT = 2048;
    private static final int TITLE_LIMIT = 300;
    private static final int TAGS_LIMIT = 500;

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:from|join)\\s+([a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)?)\\b");
    private static final Pattern TABLE_ALIAS_PATTERN = Pattern.compile(
            "\\b(?:from|join)\\s+([a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)?)\\s+(?:as\\s+)?([a-z_][a-z0-9_]*)\\b");
    private static final Pattern CTE_PATTERN = Pattern.compile(
            "(?:\\bwith\\b|,)\\s*([a-z_][a-z0-9_]*)\\s+as\\s*\\(");
    private static final Pattern QUALIFIED_COLUMN = Pattern.compile(
            "\\b([a-z_][a-z0-9_]*)\\.([a-z_][a-z0-9_]*)\\b");
    private static final Pattern EQUALITY = Pattern.compile(
            "\\b([a-z_][a-z0-9_]*)\\.([a-z_][a-z0-9_]*)\\s*=\\s*([a-z_][a-z0-9_]*)\\.([a-z_][a-z0-9_]*)\\b");
    private static final Pattern METRIC = Pattern.compile(
            "\\b(sum|count|avg|min|max)\\s*\\(\\s*(?:distinct\\s+)?([a-z_][a-z0-9_.]*|\\*)\\s*\\)");
    private static final Pattern WORD = Pattern.compile("[a-z_][a-z0-9_]*");

    private static final Set<String> ALIAS_STOP = Set.of(
            "where", "on", "join", "left", "right", "inner", "outer", "full", "cross",
            "group", "order", "having", "limit", "offset", "union", "window", "using",
            "set", "values", "as");

    private final Map<String, ParsedSql> parseCache = new ConcurrentHashMap<>();
    private final Map<String, ExtractionResult> resultCache = new ConcurrentHashMap<>();

    public record Fact(String type, String value) {
    }

    public record ExtractionResult(
            String title,
            String dialect,
            String intent,
            Set<String> tables,
            Set<String> columns,
            Set<String> tags,
            Set<String> metrics,
            Set<String> relations,
            List<Fact> facts,
            double confidence
    ) {
        public Set<String> factKeys() {
            Set<String> keys = new LinkedHashSet<>();
            for (Fact fact : facts) {
                keys.add(fact.type() + ":" + fact.value());
            }
            return keys;
        }

        public String tagsCsv() {
            return String.join(",", tags);
        }
    }

    /**
     * Cumulative policy used by the ten-round lab. The production extractor uses
     * {@link #finalPolicy()}; the baseline is intentionally surface-level, so the
     * accuracy delta measures the extraction rules rather than a changed corpus.
     */
    public record Policy(
            boolean allTables,
            boolean canonicalIntent,
            boolean columns,
            boolean relations,
            boolean metrics,
            boolean cache
    ) {
        public static Policy baseline() {
            return new Policy(false, false, false, false, false, false);
        }

        public static Policy finalPolicy() {
            return new Policy(true, true, true, true, true, true);
        }

        public Policy allTables(boolean value) {
            return new Policy(value, canonicalIntent, columns, relations, metrics, cache);
        }

        public Policy canonicalIntent(boolean value) {
            return new Policy(allTables, value, columns, relations, metrics, cache);
        }

        public Policy columns(boolean value) {
            return new Policy(allTables, canonicalIntent, value, relations, metrics, cache);
        }

        public Policy relations(boolean value) {
            return new Policy(allTables, canonicalIntent, columns, value, metrics, cache);
        }

        public Policy metrics(boolean value) {
            return new Policy(allTables, canonicalIntent, columns, relations, value, cache);
        }

        public Policy cache(boolean value) {
            return new Policy(allTables, canonicalIntent, columns, relations, metrics, value);
        }
    }

    private record ParsedSql(
            List<String> tables,
            Map<String, String> aliases,
            Set<String> columns,
            Set<String> tags,
            List<String> metrics,
            List<String> relations
    ) {
    }

    public ExtractionResult extract(String sql, String problem, String tags, String dialectHint) {
        return extract(sql, problem, tags, dialectHint, Policy.finalPolicy());
    }

    public ExtractionResult extract(String sql, String problem, String tags, String dialectHint,
                                    Policy policy) {
        String safeSql = sql == null ? "" : sql;
        String safeProblem = problem == null ? "" : problem;
        String safeTags = tags == null ? "" : tags;
        String cacheKey = policy.cache()
                ? (safeSql + "\u0000" + safeProblem + "\u0000" + safeTags + "\u0000"
                   + (dialectHint == null ? "" : dialectHint) + "\u0000" + policy)
                : null;
        if (cacheKey != null) {
            ExtractionResult cached = resultCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        ParsedSql parsed = parse(safeSql, policy);
        String dialect = normalizeDialect(dialectHint, safeSql);
        String intent = policy.canonicalIntent()
                ? canonicalIntent(safeProblem, safeSql, safeTags)
                : naiveIntent(safeProblem);
        List<String> tables = policy.allTables() ? parsed.tables() : firstTable(parsed.tables());
        Set<String> columns = policy.columns() ? parsed.columns() : Set.of();
        List<String> metrics = policy.metrics() ? parsed.metrics() : List.of();
        List<String> relations = policy.relations() ? parsed.relations() : List.of();

        Set<String> facts = new LinkedHashSet<>();
        facts.add("DIALECT:" + dialect);
        for (String table : tables) {
            facts.add("TABLE:" + table);
        }
        for (String tag : parsed.tags()) {
            facts.add("TAG:" + tag);
        }
        if (!intent.isBlank()) {
            facts.add("INTENT:" + intent);
        }
        for (String column : columns) {
            facts.add("COLUMN:" + column);
        }
        for (String metric : metrics) {
            facts.add("METRIC:" + metric);
        }
        for (String relation : relations) {
            facts.add("RELATION:" + relation);
        }

        List<Fact> ordered = new ArrayList<>();
        for (String key : facts) {
            int split = key.indexOf(':');
            ordered.add(new Fact(key.substring(0, split), key.substring(split + 1)));
        }
        double confidence = Math.min(0.99, 0.45 + 0.045 * facts.size());
        String title = title(safeProblem, tables, intent);
        String normalizedTags = mergeTags(parsed.tags(), safeTags);
        ExtractionResult result = new ExtractionResult(title, dialect, intent, Set.copyOf(tables),
                Set.copyOf(columns), Set.copyOf(parsed.tags()), Set.copyOf(metrics),
                Set.copyOf(relations), List.copyOf(ordered), confidence);
        if (cacheKey != null) {
            if (resultCache.size() >= CACHE_LIMIT) {
                resultCache.clear();
            }
            resultCache.put(cacheKey, result);
        }
        return result;
    }

    /**
     * Parses the SQL surface once per SQL text. The parse is independent of the
     * policy so a warm cache can serve every later extraction round.
     */
    private ParsedSql parse(String sql, Policy policy) {
        String cacheKey = sql;
        ParsedSql cached = parseCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String normalized = stripLiterals(sql.toLowerCase(Locale.ROOT));
        Set<String> cteNames = new LinkedHashSet<>();
        Matcher cteMatcher = CTE_PATTERN.matcher(normalized);
        while (cteMatcher.find()) {
            cteNames.add(cteMatcher.group(1));
        }

        LinkedHashSet<String> tables = new LinkedHashSet<>();
        Matcher tableMatcher = TABLE_PATTERN.matcher(normalized);
        while (tableMatcher.find()) {
            String table = tableMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!cteNames.contains(table) && !table.startsWith("(")) {
                tables.add(table);
            }
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        Matcher aliasMatcher = TABLE_ALIAS_PATTERN.matcher(normalized);
        while (aliasMatcher.find()) {
            String table = aliasMatcher.group(1).toLowerCase(Locale.ROOT);
            String alias = aliasMatcher.group(2).toLowerCase(Locale.ROOT);
            if (cteNames.contains(table) || ALIAS_STOP.contains(alias) || table.equals(alias)) {
                continue;
            }
            aliases.put(alias, table);
        }
        for (String table : tables) {
            aliases.putIfAbsent(table, table);
            int dot = table.lastIndexOf('.');
            if (dot >= 0) {
                aliases.putIfAbsent(table.substring(dot + 1), table);
            }
        }

        LinkedHashSet<String> columns = new LinkedHashSet<>();
        Matcher columnMatcher = QUALIFIED_COLUMN.matcher(normalized);
        while (columnMatcher.find()) {
            String owner = resolve(columnMatcher.group(1), aliases);
            if (!tables.contains(owner)) {
                continue;
            }
            columns.add(owner + "." + columnMatcher.group(2));
        }

        LinkedHashSet<String> metrics = new LinkedHashSet<>();
        Matcher metricMatcher = METRIC.matcher(normalized);
        while (metricMatcher.find()) {
            String fn = metricMatcher.group(1);
            String arg = metricMatcher.group(2);
            if ("*".equals(arg)) {
                metrics.add(fn);
            } else {
                int dot = arg.lastIndexOf('.');
                metrics.add(fn + "." + (dot >= 0 ? arg.substring(dot + 1) : arg));
            }
        }

        LinkedHashSet<String> relations = new LinkedHashSet<>();
        Matcher equalityMatcher = EQUALITY.matcher(normalized);
        while (equalityMatcher.find()) {
            String leftOwner = resolve(equalityMatcher.group(1), aliases);
            String rightOwner = resolve(equalityMatcher.group(3), aliases);
            if (!tables.contains(leftOwner) || !tables.contains(rightOwner)) {
                continue;
            }
            relations.add(leftOwner + "." + equalityMatcher.group(2)
                    + "=" + rightOwner + "." + equalityMatcher.group(4));
        }

        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (normalized.contains(" join ")) {
            tags.add("join");
        }
        if (normalized.contains(" group by") || normalized.contains(" having ")
                || normalized.matches("(?s).*\\b(sum|count|avg|min|max)\\s*\\(.*")) {
            tags.add("aggregation");
        }
        if (normalized.contains(" where ") || normalized.contains(" having ")) {
            tags.add("filter");
        }
        if (normalized.matches("(?s).*\\b(limit|offset)\\b.*")) {
            tags.add("pagination");
        }
        if (!cteNames.isEmpty() || normalized.stripLeading().startsWith("with ")) {
            tags.add("cte");
        }
        if (normalized.matches("(?s).*\\bover\\s*\\(.*")) {
            tags.add("window");
        }
        if (normalized.matches("(?s).*\\b(date|interval|current_date|datediff|date_trunc)\\b.*")) {
            tags.add("date");
        }
        if (normalized.contains("distinct ")) {
            tags.add("distinct");
        }

        ParsedSql parsed = new ParsedSql(List.copyOf(tables), Map.copyOf(aliases),
                Set.copyOf(columns), Set.copyOf(tags), List.copyOf(metrics), List.copyOf(relations));
        if (parseCache.size() >= CACHE_LIMIT) {
            parseCache.clear();
        }
        parseCache.put(cacheKey, parsed);
        return parsed;
    }

    private String resolve(String token, Map<String, String> aliases) {
        return aliases.getOrDefault(token, token);
    }

    private List<String> firstTable(List<String> tables) {
        return tables.isEmpty() ? List.of() : List.of(tables.get(0));
    }

    private String title(String problem, List<String> tables, String intent) {
        String base = problem.replaceAll("\\s+", " ").trim();
        if (base.isBlank()) {
            base = "SQL pattern " + (tables.isEmpty() ? "" : tables.get(0) + " ") + intent;
        }
        return base.length() <= TITLE_LIMIT ? base : base.substring(0, TITLE_LIMIT);
    }

    private String normalizeDialect(String hint, String sql) {
        String value = hint == null ? "" : hint.trim().toUpperCase(Locale.ROOT);
        if (value.contains("POSTGRES")) {
            return "POSTGRESQL";
        }
        if (value.contains("MYSQL") || value.contains("MARIADB")) {
            return "MYSQL";
        }
        if (value.equals("H2")) {
            return "H2";
        }
        if (!value.isBlank()) {
            return value.length() > 32 ? value.substring(0, 32) : value;
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        if (lower.contains("::") || lower.contains("date_trunc")) {
            return "POSTGRESQL";
        }
        if (lower.contains("`") || lower.contains("ifnull(")) {
            return "MYSQL";
        }
        return "H2";
    }

    private String naiveIntent(String problem) {
        String value = problem.replaceAll("\\s+", " ").trim();
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private String canonicalIntent(String problem, String sql, String tags) {
        String text = (problem + " " + tags).toLowerCase(Locale.ROOT);
        if (text.contains("留存") || text.contains("retention") || text.contains("cohort")) {
            return "retention_by_cohort";
        }
        if (text.contains("转化") || text.contains("conversion") || text.contains("funnel")) {
            return text.contains("渠道") || text.contains("channel")
                    ? "conversion_by_channel" : "conversion";
        }
        if (text.contains("分层") || text.contains("segment") || text.contains("客群")) {
            return "customer_segmentation";
        }
        if (text.contains("退款") || text.contains("refund")) {
            return "refund_rate";
        }
        if (text.contains("促销") || text.contains("promotion")) {
            return "promotion_revenue";
        }
        if (text.contains("缺货") || text.contains("库存") || text.contains("stock")) {
            return "stockout_risk";
        }
        if (text.contains("物流") || text.contains("配送") || text.contains("delivery")
                || text.contains("shipment")) {
            return "delivery_delay";
        }
        if (text.contains("归因") || text.contains("attribution")
                || ((text.contains("渠道") || text.contains("channel"))
                    && (text.contains("gmv") || text.contains("成交") || text.contains("销售额")))) {
            return "revenue_by_channel";
        }
        if (text.contains("排名") || text.contains("ranking") || text.contains("top")) {
            return "product_ranking";
        }
        if (text.contains("缺失") || text.contains("null") || text.contains("质量")
                || text.contains("quality")) {
            return "data_quality_nulls";
        }
        if (text.contains("慢") || text.contains("失败") || text.contains("diagnos")
                || text.contains("异常") || text.contains("slow")) {
            return "slow_query_diagnosis";
        }
        if (text.contains("gmv") || text.contains("成交") || text.contains("销售额")
                || text.contains("revenue")) {
            return text.contains("区域") || text.contains("region")
                    ? "revenue_by_region" : "revenue";
        }
        return "general_sql";
    }

    private String mergeTags(Set<String> extracted, String extra) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(extracted);
        if (extra != null && !extra.isBlank()) {
            for (String tag : extra.split("[,，]")) {
                String value = tag.trim();
                if (!value.isBlank()) {
                    merged.add(value.length() > 40 ? value.substring(0, 40) : value);
                }
            }
        }
        String joined = String.join(",", merged);
        return joined.length() <= TAGS_LIMIT ? joined : joined.substring(0, TAGS_LIMIT);
    }

    private String stripLiterals(String sql) {
        return sql.replaceAll("'(?:''|[^'])*'", "'?'");
    }

    /** Visible for tests: clear the query-shape caches without touching semantics. */
    public void clearCache() {
        parseCache.clear();
        resultCache.clear();
    }

    public int cacheSize() {
        return resultCache.size();
    }

    /** Extracts one fact key per line; convenient for logs and corpus assertions. */
    public static Set<String> factKeys(ExtractionResult result) {
        return result.factKeys();
    }

    /** Returns the words of a SQL statement; used by evidence coverage checks. */
    public static Set<String> words(String text) {
        Set<String> words = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }
}
