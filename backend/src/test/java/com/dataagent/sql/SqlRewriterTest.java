package com.dataagent.sql;

import com.dataagent.config.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlRewriterTest {

    private SqlRewriter rewriter;

    @BeforeEach
    void setUp() {
        rewriter = new SqlRewriter(new DataAgentProperties());
    }

    @Test
    void appendsLimitToFullScan() {
        SqlRewriter.RewriteResult result = rewriter.rewrite("SELECT * FROM orders");
        assertTrue(result.rewrittenSql().toUpperCase().endsWith("LIMIT 5000"));
        assertTrue(result.applied().stream().anyMatch(r -> r.type().equals("CAP_FULL_SCAN")));
    }

    @Test
    void appendsLimitToOrderByWithoutLimit() {
        SqlRewriter.RewriteResult result = rewriter.rewrite("SELECT id FROM orders ORDER BY created_at");
        assertTrue(result.rewrittenSql().toUpperCase().endsWith("LIMIT 5000"));
        assertTrue(result.applied().stream().anyMatch(r -> r.type().equals("ADD_LIMIT")));
    }

    @Test
    void leavesExistingLimitUntouched() {
        SqlRewriter.RewriteResult result = rewriter.rewrite("SELECT id FROM orders LIMIT 10");
        assertEquals("SELECT id FROM orders LIMIT 10", result.rewrittenSql());
        assertTrue(result.applied().isEmpty());
    }

    @Test
    void aggregateOnlyQueryIsNotRewritten() {
        SqlRewriter.RewriteResult result = rewriter.rewrite("SELECT COUNT(*) FROM orders");
        assertEquals("SELECT COUNT(*) FROM orders", result.rewrittenSql());
    }

    @Test
    void stripsTrailingSemicolons() {
        assertEquals("SELECT 1", rewriter.rewrite("SELECT 1;;").rewrittenSql());
    }

    @Test
    void flagsUnionAsSuggestionNotRewrite() {
        SqlRewriter.RewriteResult result = rewriter.rewrite("SELECT id FROM a UNION SELECT id FROM b");
        assertFalse(result.rewrittenSql().contains("UNION ALL"));
        assertTrue(result.suggestions().stream().anyMatch(s -> s.contains("UNION ALL")));
    }
}
