package com.dataagent.sql;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGuardTest {

    private SqlGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SqlGuard(new DataAgentProperties());
    }

    @Test
    void allowsReadOnlyQueries() {
        assertEquals("SELECT", guard.guardForExecution("select * from t where id = 1").statementType());
        assertEquals("WITH", guard.guardForExecution("WITH x AS (SELECT 1) SELECT * FROM x").statementType());
        assertEquals("VALUES", guard.guardForExecution("VALUES (1), (2)").statementType());
    }

    @Test
    void blocksWritesAndMultiStatement() {
        assertThrows(ApiException.class, () -> guard.guardForExecution("UPDATE t SET x = 1"));
        assertThrows(ApiException.class, () -> guard.guardForExecution("DELETE FROM t"));
        assertThrows(ApiException.class, () -> guard.guardForExecution("SELECT 1; DROP TABLE t"));
        assertThrows(ApiException.class, () -> guard.guardForExecution("WITH x AS (DELETE FROM t RETURNING *) SELECT * FROM x"));
    }

    @Test
    void blocksSelectIntoAndExplainAnalyze() {
        assertThrows(ApiException.class, () -> guard.guardForExecution("SELECT * INTO backup FROM t"));
        assertThrows(ApiException.class, () -> guard.guardForExplain("EXPLAIN ANALYZE SELECT * FROM t"));
        assertThrows(ApiException.class, () -> guard.guardForExecution("SELECT pg_sleep(10)"));
    }

    @Test
    void ignoresKeywordsInsideStringLiteralsAndComments() {
        assertEquals("SELECT", guard.guardForExecution("SELECT 'delete from t' AS msg -- drop table x\nFROM t").statementType());
        assertEquals("SELECT", guard.guardForExecution("SELECT 1 /* update t set x=1 */").statementType());
    }

    @Test
    void stripsOuterExplainAndKeepsInnerSql() {
        SqlGuard.GuardedSql guarded = guard.guardForExplain("EXPLAIN (FORMAT JSON) SELECT * FROM t");
        assertEquals("SELECT * FROM t", guarded.sql());
    }

    @Test
    void fingerprintIsStableAcrossWhitespaceAndCase() {
        String first = guard.fingerprint("SELECT  * FROM t WHERE id = 1");
        String second = guard.fingerprint("select *\nfrom t where id = 2");
        assertEquals(first, second);
    }

    @Test
    void rejectsEmptyAndTooLongSql() {
        assertThrows(ApiException.class, () -> guard.guardForExecution("   "));
        DataAgentProperties properties = new DataAgentProperties();
        properties.getQuery().setMaxSqlLength(10);
        SqlGuard small = new SqlGuard(properties);
        assertThrows(ApiException.class, () -> small.guardForExecution("SELECT 1234567890"));
    }

    @Test
    void normalizesTrailingSemicolon() {
        assertTrue(guard.guardForExecution("SELECT 1;").sql().equals("SELECT 1"));
    }
}
