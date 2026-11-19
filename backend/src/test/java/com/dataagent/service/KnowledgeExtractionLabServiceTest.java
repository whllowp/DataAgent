package com.dataagent.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeExtractionLabServiceTest {

    private final KnowledgeExtractionService extractor = new KnowledgeExtractionService();
    private final KnowledgeExtractionLabService lab = new KnowledgeExtractionLabService(extractor);

    @Test
    void extractsSchemaAwareFactsFromAJoin() {
        KnowledgeExtractionService.ExtractionResult result = extractor.extract(
                "SELECT c.region, SUM(o.amount) FROM orders o "
                        + "JOIN customers c ON o.customer_id = c.id "
                        + "WHERE o.status = 'PAID' GROUP BY c.region",
                "按区域统计 GMV", "", "POSTGRESQL");

        Set<String> facts = result.factKeys();
        assertTrue(facts.contains("TABLE:orders"));
        assertTrue(facts.contains("TABLE:customers"));
        assertTrue(facts.contains("COLUMN:customers.region"));
        assertTrue(facts.contains("METRIC:sum.amount"));
        assertTrue(facts.contains("RELATION:orders.customer_id=customers.id"));
        assertEquals("revenue_by_region", result.intent());
    }

    @Test
    void tenRoundsExceedAccuracyAndPerformanceTargets() {
        KnowledgeExtractionLabService.Report report = lab.run(10);
        System.out.println("EXTRACTION_LAB " + report);

        assertEquals(10, report.rounds());
        assertEquals(12, report.cases());
        assertTrue(report.accuracyGainPct() >= 50.0,
                "accuracy gain should be >= 50%, was " + report.accuracyGainPct());
        assertTrue(report.performanceGainPct() >= 40.0,
                "performance gain should be >= 40%, was " + report.performanceGainPct());
        assertEquals(10, report.evolution().size());
        assertTrue(report.evolution().stream().anyMatch(KnowledgeExtractionLabService.Round::retained));
    }
}
