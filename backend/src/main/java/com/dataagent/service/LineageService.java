package com.dataagent.service;

import com.dataagent.domain.SemanticEntity;
import com.dataagent.domain.SqlAsset;
import com.dataagent.domain.VerifiedQuery;
import com.dataagent.repository.SemanticEntityRepository;
import com.dataagent.repository.SqlAssetRepository;
import com.dataagent.repository.VerifiedQueryRepository;
import com.dataagent.web.dto.LineageDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LineageService {

    private static final Pattern CTE = Pattern.compile(
            "(?i)(?:\\bWITH\\b|,)\\s*([a-z_][\\w$]*)\\s+AS\\s*\\(");
    private static final Pattern TABLE = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|UPDATE|INTO|DELETE\\s+FROM|MERGE\\s+INTO)\\s+([a-z_][\\w$]*(?:\\.[a-z_][\\w$]*)?)");

    private final SqlAssetRepository assets;
    private final VerifiedQueryRepository verifiedQueries;
    private final SemanticEntityRepository entities;

    public LineageService(SqlAssetRepository assets, VerifiedQueryRepository verifiedQueries,
                          SemanticEntityRepository entities) {
        this.assets = assets;
        this.verifiedQueries = verifiedQueries;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public List<LineageDtos.TableSummary> tables() {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (SqlAsset asset : assets.findAll()) {
            for (String table : extractTables(asset.getSqlText())) {
                counts.computeIfAbsent(table, ignored -> new int[3])[0]++;
            }
        }
        for (VerifiedQuery query : verifiedQueries.findAll()) {
            for (String table : extractTables(query.getSqlText())) {
                counts.computeIfAbsent(table, ignored -> new int[3])[1]++;
            }
        }
        for (SemanticEntity entity : entities.findAll()) {
            for (String table : splitTables(entity.getPhysicalTables())) {
                counts.computeIfAbsent(table, ignored -> new int[3])[2]++;
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new LineageDtos.TableSummary(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2]))
                .sorted(Comparator.comparing(LineageDtos.TableSummary::table))
                .toList();
    }

    @Transactional(readOnly = true)
    public LineageDtos.Impact impact(String table) {
        String normalized = table == null ? "" : canonical(table);
        List<LineageDtos.AssetRef> assetRefs = assets.findAll().stream()
                .filter(asset -> references(asset.getSqlText(), normalized))
                .sorted(Comparator.comparing(SqlAsset::getLastSeen,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(asset -> new LineageDtos.AssetRef(asset.getId(), asset.getBusinessProblem(),
                        preview(asset.getSqlText()), asset.getDatasourceId(),
                        asset.getExecutionCount(), asset.getLastSeen()))
                .toList();
        List<LineageDtos.VerifiedRef> verifiedRefs = verifiedQueries.findAll().stream()
                .filter(query -> references(query.getSqlText(), normalized))
                .map(query -> new LineageDtos.VerifiedRef(query.getId(), query.getQuestion(),
                        query.getStatus()))
                .toList();
        List<LineageDtos.EntityRef> entityRefs = entities.findAll().stream()
                .filter(entity -> splitTables(entity.getPhysicalTables()).contains(normalized))
                .map(entity -> new LineageDtos.EntityRef(entity.getId(), entity.getName(),
                        entity.getDisplayName(), entity.getPhysicalTables()))
                .toList();
        return new LineageDtos.Impact(normalized, assetRefs, verifiedRefs, entityRefs);
    }

    public Set<String> extractTables(String sql) {
        Set<String> result = new LinkedHashSet<>();
        if (sql == null || sql.isBlank()) {
            return result;
        }
        String cleaned = strip(sql);
        Set<String> ctes = new LinkedHashSet<>();
        Matcher cteMatcher = CTE.matcher(cleaned);
        while (cteMatcher.find()) {
            ctes.add(cteMatcher.group(1).toUpperCase(Locale.ROOT));
        }
        Matcher matcher = TABLE.matcher(cleaned);
        while (matcher.find()) {
            String table = canonical(matcher.group(1));
            if (!ctes.contains(table) && !table.startsWith("SELECT")) {
                result.add(table);
            }
        }
        return result;
    }

    private static String canonical(String table) {
        String upper = table.toUpperCase(Locale.ROOT);
        int dot = upper.lastIndexOf('.');
        return dot >= 0 ? upper.substring(dot + 1) : upper;
    }

    private boolean references(String sql, String table) {
        return extractTables(sql).contains(table);
    }

    private static List<String> splitTables(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String piece : value.split("[,，;；\\s]+")) {
            if (!piece.isBlank()) {
                result.add(canonical(piece.trim()));
            }
        }
        return result;
    }

    private static String strip(String sql) {
        return sql.replaceAll("--[^\\n]*", " ")
                .replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("'(?:''|[^'])*'", "''");
    }

    private static String preview(String sql) {
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 240 ? oneLine : oneLine.substring(0, 240) + "…";
    }
}
