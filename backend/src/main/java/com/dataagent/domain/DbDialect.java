package com.dataagent.domain;

import java.util.Locale;

public enum DbDialect {
    H2,
    POSTGRESQL,
    MYSQL;

    public static DbDialect from(String value) {
        if (value == null || value.isBlank()) {
            return H2;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POSTGRES", "POSTGRESQL", "PG", "OPENGAUSS", "GAUSSDB", "DWS" -> POSTGRESQL;
            case "MYSQL", "MARIADB" -> MYSQL;
            case "H2" -> H2;
            default -> throw new IllegalArgumentException("不支持的数据库方言: " + value);
        };
    }

    public static DbDialect infer(String jdbcUrl) {
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:opengauss:")) {
            return POSTGRESQL;
        }
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            return MYSQL;
        }
        if (url.startsWith("jdbc:h2:")) {
            return H2;
        }
        throw new IllegalArgumentException("无法从 JDBC URL 推断方言，请显式传入 dialect（H2/POSTGRESQL/MYSQL）");
    }

    public String explainPrefix() {
        return switch (this) {
            case POSTGRESQL -> "EXPLAIN (FORMAT JSON, COSTS TRUE, VERBOSE FALSE) ";
            case MYSQL -> "EXPLAIN FORMAT=JSON ";
            case H2 -> "EXPLAIN ";
        };
    }

    public String planFormat() {
        return switch (this) {
            case POSTGRESQL -> "POSTGRES_JSON";
            case MYSQL -> "MYSQL_JSON";
            case H2 -> "TEXT";
        };
    }

    public String defaultSchema() {
        return switch (this) {
            case POSTGRESQL -> "public";
            case MYSQL -> null;
            case H2 -> "PUBLIC";
        };
    }
}
