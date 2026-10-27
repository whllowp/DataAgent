package com.dataagent.service;

import com.dataagent.domain.Datasource;
import com.dataagent.domain.DbDialect;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.MetadataDtos;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
public class MetadataService {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "pg_catalog", "pg_toast", "sys", "mysql", "performance_schema",
            "innodb", "sys_config");

    private final DatasourceService datasources;

    public MetadataService(DatasourceService datasources) {
        this.datasources = datasources;
    }

    public List<String> listSchemas(UUID datasourceId) {
        Datasource datasource = datasources.getRequired(datasourceId);
        DbDialect dialect = DbDialect.from(datasource.getDialect());
        try (Connection connection = datasources.dataSourceFor(datasourceId).getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<String> result = new ArrayList<>();
            if (dialect == DbDialect.MYSQL) {
                try (ResultSet rs = metaData.getCatalogs()) {
                    while (rs.next()) {
                        String catalog = rs.getString("TABLE_CAT");
                        if (catalog != null && !SYSTEM_SCHEMAS.contains(catalog.toLowerCase(Locale.ROOT))) {
                            result.add(catalog);
                        }
                    }
                }
            } else {
                try (ResultSet rs = metaData.getSchemas()) {
                    while (rs.next()) {
                        String schema = rs.getString("TABLE_SCHEM");
                        if (schema != null && !isSystemSchema(schema)) {
                            result.add(schema);
                        }
                    }
                }
            }
            if (result.isEmpty()) {
                String fallback = dialect.defaultSchema();
                if (fallback != null) {
                    result.add(fallback);
                }
            }
            return result.stream().distinct().sorted().toList();
        } catch (SQLException e) {
            throw ApiException.badRequest("读取 schema 失败: " + e.getMessage());
        }
    }

    public List<MetadataDtos.TableInfo> listTables(UUID datasourceId, String schema, String query) {
        Datasource datasource = datasources.getRequired(datasourceId);
        DbDialect dialect = DbDialect.from(datasource.getDialect());
        String schemaName = resolveSchema(schema, dialect);
        String catalog = null;
        try (Connection connection = datasources.dataSourceFor(datasourceId).getConnection()) {
            if (dialect == DbDialect.MYSQL) {
                catalog = schemaName != null ? schemaName : connection.getCatalog();
                schemaName = null;
            }
            DatabaseMetaData metaData = connection.getMetaData();
            String displaySchema = dialect == DbDialect.MYSQL ? catalog : schemaName;
            List<MetadataDtos.TableInfo> tables = new ArrayList<>();
            String pattern = dialect == DbDialect.H2 ? "%" : "%";
            try (ResultSet rs = metaData.getTables(catalog, schemaName, pattern,
                    new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"})) {
                while (rs.next()) {
                    String tableSchema = rs.getString("TABLE_SCHEM");
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName == null) {
                        continue;
                    }
                    if (schemaName == null && tableSchema != null && isSystemSchema(tableSchema)) {
                        continue;
                    }
                    if (query != null && !query.isBlank()
                            && !tableName.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    String type = rs.getString("TABLE_TYPE");
                    String remarks = rs.getString("REMARKS");
                    tables.add(new MetadataDtos.TableInfo(
                            tableSchema == null ? displaySchema : tableSchema,
                            tableName,
                            type,
                            remarks));
                }
            }
            tables.sort(Comparator.comparing(MetadataDtos.TableInfo::name, String.CASE_INSENSITIVE_ORDER));
            if (tables.size() > 2000) {
                return tables.subList(0, 2000);
            }
            return tables;
        } catch (SQLException e) {
            throw ApiException.badRequest("读取表列表失败: " + e.getMessage());
        }
    }

    public MetadataDtos.TableDetail describeTable(UUID datasourceId, String schema, String table) {
        Datasource datasource = datasources.getRequired(datasourceId);
        DbDialect dialect = DbDialect.from(datasource.getDialect());
        String schemaName = resolveSchema(schema, dialect);
        try (Connection connection = datasources.dataSourceFor(datasourceId).getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = dialect == DbDialect.MYSQL
                    ? (schemaName != null ? schemaName : connection.getCatalog())
                    : null;
            String actualSchema = dialect == DbDialect.MYSQL ? null : schemaName;
            String actualTable = resolveTableName(metaData, catalog, actualSchema, table, dialect);
            if (actualTable == null) {
                throw ApiException.notFound("表不存在: " + (schemaName == null ? "" : schemaName + ".") + table);
            }

            Set<String> primaryKeys = new LinkedHashSet<>();
            try (ResultSet rs = metaData.getPrimaryKeys(catalog, actualSchema, actualTable)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }

            List<MetadataDtos.ColumnInfo> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(catalog, actualSchema, actualTable, "%")) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    columns.add(new MetadataDtos.ColumnInfo(
                            columnName,
                            rs.getString("TYPE_NAME"),
                            rs.getInt("DATA_TYPE"),
                            rs.getInt("ORDINAL_POSITION"),
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            primaryKeys.contains(columnName),
                            rs.getString("COLUMN_DEF"),
                            rs.getString("REMARKS")));
                }
            }
            columns.sort(Comparator.comparingInt(MetadataDtos.ColumnInfo::ordinal));

            Map<String, IndexBuilder> indexes = new LinkedHashMap<>();
            try (ResultSet rs = metaData.getIndexInfo(catalog, actualSchema, actualTable, false, true)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    if (indexName == null || columnName == null) {
                        continue;
                    }
                    IndexBuilder builder = indexes.computeIfAbsent(indexName, key -> new IndexBuilder(
                            !rsBoolean(rs, "NON_UNIQUE")));
                    builder.columns.add(columnName);
                }
            }
            List<MetadataDtos.IndexInfo> indexInfos = indexes.entrySet().stream()
                    .map(entry -> new MetadataDtos.IndexInfo(entry.getKey(), entry.getValue().unique,
                            List.copyOf(entry.getValue().columns)))
                    .toList();

            String remarks = null;
            String type = "TABLE";
            try (ResultSet rs = metaData.getTables(catalog, actualSchema, actualTable,
                    new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"})) {
                if (rs.next()) {
                    remarks = rs.getString("REMARKS");
                    type = rs.getString("TABLE_TYPE");
                    actualSchema = rs.getString("TABLE_SCHEM") == null ? actualSchema : rs.getString("TABLE_SCHEM");
                }
            }
            return new MetadataDtos.TableDetail(actualSchema, actualTable, type, remarks, columns, indexInfos);
        } catch (SQLException e) {
            throw ApiException.badRequest("读取表结构失败: " + e.getMessage());
        }
    }

    private String resolveSchema(String schema, DbDialect dialect) {
        if (schema != null && !schema.isBlank()) {
            return schema;
        }
        return dialect.defaultSchema();
    }

    private String resolveTableName(DatabaseMetaData metaData, String catalog, String schema,
                                    String table, DbDialect dialect) throws SQLException {
        List<String> candidates = new ArrayList<>();
        candidates.add(table);
        if (dialect == DbDialect.H2 || dialect == DbDialect.POSTGRESQL) {
            candidates.add(table.toUpperCase(Locale.ROOT));
            candidates.add(table.toLowerCase(Locale.ROOT));
        }
        for (String candidate : candidates) {
            try (ResultSet rs = metaData.getTables(catalog, schema, candidate,
                    new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"})) {
                if (rs.next()) {
                    return rs.getString("TABLE_NAME");
                }
            }
        }
        return null;
    }

    private boolean isSystemSchema(String schema) {
        String lower = schema.toLowerCase(Locale.ROOT);
        return SYSTEM_SCHEMAS.contains(lower) || lower.startsWith("pg_") || lower.startsWith("information_schema");
    }

    private boolean rsBoolean(ResultSet rs, String column) {
        try {
            return rs.getBoolean(column);
        } catch (SQLException e) {
            return false;
        }
    }

    private static final class IndexBuilder {
        private final boolean unique;
        private final List<String> columns = new ArrayList<>();

        private IndexBuilder(boolean unique) {
            this.unique = unique;
        }
    }
}
