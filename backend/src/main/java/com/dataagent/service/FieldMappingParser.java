package com.dataagent.service;

import com.dataagent.web.dto.EtlDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic field-level mapper for INSERT ... SELECT ETL SQL. It maps the
 * select-list expressions (and optional explicit target column list) to
 * source columns, keeping wildcard/expression mappings visible as such.
 */
@Service
public class FieldMappingParser {

    private static final Pattern INSERT_COLUMNS = Pattern.compile(
            "(?i)\\bINSERT\\s+(?:OVERWRITE\\s+)?(?:INTO\\s+)?(?:TABLE\\s+)?[a-z_][\\w$]*(?:\\.[a-z_][\\w$]*)?\\s*\\(([^)]*)\\)");
    private static final Pattern ALIAS = Pattern.compile("(?is)^(.*?)\\s+AS\\s+([a-z_][\\w$]*)$");
    private static final Pattern TRAILING_ALIAS = Pattern.compile("(?is)^(.*?)\\s+([a-z_][\\w$]*)$");
    private static final Pattern QUALIFIED = Pattern.compile("(?i)\\b([a-z_][\\w$]*)\\.([a-z_][\\w$]*)\\b");
    private static final Pattern IDENTIFIER = Pattern.compile("(?i)^[a-z_][\\w$]*$");

    public List<EtlDtos.FieldMapping> parse(String sql, LineageService lineage) {
        List<EtlDtos.FieldMapping> result = new ArrayList<>();
        if (sql == null || sql.isBlank()) {
            return result;
        }
        String targetTable = lineage.extractTargetTable(sql);
        if (targetTable == null) {
            return result;
        }
        String cleaned = strip(sql);
        Map<String, String> aliases = lineage.tableAliases(cleaned);
        List<String> targetColumns = insertColumns(cleaned);
        String select = selectList(cleaned);
        if (select == null || select.isBlank() || "*".equals(select.trim())) {
            result.add(new EtlDtos.FieldMapping(null, "*", targetTable, "*", "*", true));
            return result;
        }
        List<String> expressions = splitTopLevel(select);
        for (int i = 0; i < expressions.size(); i++) {
            String raw = expressions.get(i).trim();
            if (raw.isBlank()) {
                continue;
            }
            String expression = raw;
            String alias = null;
            Matcher asMatcher = ALIAS.matcher(raw);
            if (asMatcher.matches()) {
                expression = asMatcher.group(1).trim();
                alias = asMatcher.group(2);
            } else {
                Matcher trailing = TRAILING_ALIAS.matcher(raw);
                if (trailing.matches() && !raw.endsWith(")") && !raw.contains(" ")) {
                    expression = trailing.group(1).trim();
                    alias = trailing.group(2);
                }
            }
            String targetColumn = i < targetColumns.size() ? targetColumns.get(i)
                    : (alias == null ? "COL_" + (i + 1) : alias.toUpperCase(Locale.ROOT));
            boolean wildcard = expression.equals("*") || expression.endsWith(".*");
            String sourceTable = null;
            String sourceColumn = null;
            if (!wildcard) {
                Matcher qualified = QUALIFIED.matcher(expression);
                while (qualified.find()) {
                    String table = aliases.get(qualified.group(1).toLowerCase(Locale.ROOT));
                    if (table != null) {
                        sourceTable = table;
                        sourceColumn = qualified.group(2).toUpperCase(Locale.ROOT);
                        break;
                    }
                }
                if (sourceTable == null && IDENTIFIER.matcher(expression).matches()) {
                    sourceColumn = expression.toUpperCase(Locale.ROOT);
                }
            } else {
                sourceColumn = "*";
            }
            result.add(new EtlDtos.FieldMapping(sourceTable, sourceColumn, targetTable,
                    targetColumn.toUpperCase(Locale.ROOT), raw, wildcard));
        }
        return result;
    }

    private static List<String> insertColumns(String sql) {
        Matcher matcher = INSERT_COLUMNS.matcher(sql);
        if (!matcher.find()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : matcher.group(1).split(",")) {
            String column = value.trim().replace("`", "").replace("\"", "");
            if (!column.isBlank()) {
                int dot = column.lastIndexOf('.');
                result.add((dot >= 0 ? column.substring(dot + 1) : column).toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static String selectList(String sql) {
        int select = indexOfKeyword(sql, "SELECT", 0);
        if (select < 0) {
            return null;
        }
        int from = indexOfKeyword(sql, "FROM", select + 6);
        return from < 0 ? null : sql.substring(select + 6, from).trim();
    }

    private static int indexOfKeyword(String value, String keyword, int from) {
        int depth = 0;
        for (int i = from; i <= value.length() - keyword.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && value.regionMatches(true, i, keyword, 0, keyword.length())) {
                boolean left = i == 0 || !Character.isLetterOrDigit(value.charAt(i - 1));
                int end = i + keyword.length();
                boolean right = end >= value.length() || !Character.isLetterOrDigit(value.charAt(end));
                if (left && right) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String value) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ',' && depth == 0) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static String strip(String sql) {
        return sql.replaceAll("--[^\\n]*", " ")
                .replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("'(?:''|[^'])*'", "''");
    }
}
