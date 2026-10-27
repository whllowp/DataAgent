package com.dataagent.sql;

import com.dataagent.web.dto.OptimizeDtos;
import com.dataagent.web.dto.SqlDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnalyzerTest {

    private final SqlAnalyzer analyzer = new SqlAnalyzer();

    @Test
    void detectsCommonAntiPatterns() {
        List<OptimizeDtos.Finding> findings = analyzer.analyze(
                "SELECT * FROM customers WHERE LOWER(name) LIKE '%abc' ORDER BY created_at", null);
        assertTrue(has(findings, "SELECT_STAR"));
        assertTrue(has(findings, "FUNCTION_ON_COLUMN"));
        assertTrue(has(findings, "LEADING_WILDCARD"));
        assertTrue(has(findings, "ORDER_BY_NO_LIMIT"));
    }

    @Test
    void detectsMissingWhereAndNotIn() {
        List<OptimizeDtos.Finding> findings = analyzer.analyze("SELECT id FROM orders WHERE id NOT IN (SELECT id FROM t)", null);
        assertTrue(has(findings, "NOT_IN"));
        assertTrue(has(findings, "IN_SUBQUERY"));
        assertTrue(!has(findings, "NO_WHERE"));
        List<OptimizeDtos.Finding> noFilter = analyzer.analyze("SELECT id FROM orders", null);
        assertTrue(has(noFilter, "NO_WHERE"));
    }

    @Test
    void doesNotFlagMissingWhereForAggregateWithoutFilter() {
        List<OptimizeDtos.Finding> findings = analyzer.analyze("SELECT COUNT(*) FROM orders", null);
        assertTrue(!has(findings, "NO_WHERE"));
    }

    @Test
    void mergesPlanInsights() {
        SqlDtos.PlanView plan = new SqlDtos.PlanView("TEXT", "/* PUBLIC.ORDERS.tableScan */", "full scan",
                List.of(new SqlDtos.PlanInsight("HIGH", "H2_FULL_SCAN", "全表扫描", "detail")), 2);
        List<OptimizeDtos.Finding> findings = analyzer.analyze("SELECT id FROM orders WHERE status = 'PAID'", plan);
        assertTrue(has(findings, "PLAN_H2_FULL_SCAN"));
    }

    private boolean has(List<OptimizeDtos.Finding> findings, String code) {
        return findings.stream().anyMatch(finding -> finding.code().equals(code));
    }
}
