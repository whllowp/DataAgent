package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.repository.DataExportRepository;
import com.dataagent.sql.SqlGuard;
import com.dataagent.web.dto.SqlDtos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DataExportServiceTest {

    private DataExportService service() {
        return new DataExportService(mock(JdbcQueryService.class),
                new SqlGuard(new DataAgentProperties()),
                mock(DataExportRepository.class),
                new DataAgentProperties());
    }

    private SqlDtos.QueryResult sample() {
        List<SqlDtos.ColumnMeta> columns = List.of(
                new SqlDtos.ColumnMeta("id", "id", "BIGINT", "Long", -5),
                new SqlDtos.ColumnMeta("name", "name", "VARCHAR", "String", 12));
        List<List<Object>> rows = java.util.Arrays.asList(
                java.util.Arrays.asList(1L, "客户-\"1\""),
                java.util.Arrays.asList(2L, "a,b"),
                java.util.Arrays.asList(3L, null));
        return new SqlDtos.QueryResult(UUID.randomUUID(), "SUCCESS", columns, rows, 3,
                false, 0, 0, null, null, Instant.now(), false, List.of());
    }

    @Test
    void csvEscapesQuotesCommasAndNulls() {
        String csv = new String(service().serialize(sample(), "csv"));
        String[] lines = csv.split("\r\n|\n");
        assertEquals("id,name", lines[0]);
        assertEquals("\"客户-\"\"1\"\"\"", lines[1].substring(lines[1].indexOf(',') + 1));
        assertEquals("\"a,b\"", lines[2].substring(lines[2].indexOf(',') + 1));
        assertTrue(lines[3].endsWith(","));
    }

    @Test
    void jsonBuildsArrayOfObjectsWithEscapedQuotes() {
        String json = new String(service().serialize(sample(), "json"));
        assertTrue(json.startsWith("["));
        assertTrue(json.endsWith("]"));
        assertTrue(json.contains("\"name\":\"客户-\\\"1\\\"\""));
        assertTrue(json.contains("\"name\":null"));
    }

    @Test
    void jsonlEmitsOneObjectPerLine() {
        String jsonl = new String(service().serialize(sample(), "jsonl"));
        String[] lines = jsonl.split("\r\n|\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("{"));
        assertTrue(lines[0].endsWith("}"));
    }

    @Test
    void jsonHandlesNumbersAndBooleansAsLiterals() {
        List<SqlDtos.ColumnMeta> columns = List.of(
                new SqlDtos.ColumnMeta("n", "n", "INTEGER", "Integer", 4),
                new SqlDtos.ColumnMeta("flag", "flag", "BOOLEAN", "Boolean", 16));
        List<List<Object>> rows = List.of(List.of(42, true));
        SqlDtos.QueryResult result = new SqlDtos.QueryResult(UUID.randomUUID(), "SUCCESS",
                columns, rows, 1, false, 0, 0, null, null, Instant.now(), false, List.of());
        String json = new String(service().serialize(result, "json"));
        assertTrue(json.contains("\"n\":42"));
        assertTrue(json.contains("\"flag\":true"));
    }
}
