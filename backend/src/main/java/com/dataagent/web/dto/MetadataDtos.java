package com.dataagent.web.dto;

import java.util.List;

public final class MetadataDtos {

    private MetadataDtos() {
    }

    public record SchemaList(List<String> schemas) {
    }

    public record TableInfo(String schema, String name, String type, String remarks) {
    }

    public record TableList(List<TableInfo> tables) {
    }

    public record ColumnInfo(
            String name,
            String typeName,
            int jdbcType,
            int ordinal,
            boolean nullable,
            boolean primaryKey,
            String defaultValue,
            String remarks
    ) {
    }

    public record IndexInfo(String name, boolean unique, List<String> columns) {
    }

    public record TableDetail(
            String schema,
            String name,
            String type,
            String remarks,
            List<ColumnInfo> columns,
            List<IndexInfo> indexes
    ) {
    }
}
