package com.dataagent.service;

import com.dataagent.web.dto.MetadataDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TargetSchemaResolver {

    private final MetadataService metadata;
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public TargetSchemaResolver(MetadataService metadata) {
        this.metadata = metadata;
    }

    public List<String> columns(UUID datasourceId, String targetTable) {
        if (datasourceId == null || targetTable == null || targetTable.isBlank()) {
            return List.of();
        }
        String table = canonical(targetTable);
        String key = datasourceId + "|" + table;
        return cache.computeIfAbsent(key, ignored -> {
            try {
                MetadataDtos.TableDetail detail = metadata.describeTableInternal(datasourceId, null, table);
                List<String> columns = new ArrayList<>();
                detail.columns().stream()
                        .sorted(java.util.Comparator.comparingInt(MetadataDtos.ColumnInfo::ordinal))
                        .forEach(column -> columns.add(column.name().toUpperCase(Locale.ROOT)));
                return List.copyOf(columns);
            } catch (Exception error) {
                return List.of();
            }
        });
    }

    public void clear() {
        cache.clear();
    }

    private static String canonical(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        int dot = upper.lastIndexOf('.');
        return dot >= 0 ? upper.substring(dot + 1) : upper;
    }
}
