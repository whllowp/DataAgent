package com.dataagent.sql;

import com.dataagent.config.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlRiskAssessorTest {

    private SqlRiskAssessor assessor;

    @BeforeEach
    void setUp() {
        assessor = new SqlRiskAssessor(new DataAgentProperties());
    }

    @Test
    void safeQueryScoresLowAndIsNotBlocked() {
        SqlRiskAssessor.RiskAssessment assessment = assessor.assess("SELECT id, name FROM customers WHERE id = 1 LIMIT 10");
        assertFalse(assessment.blocked());
        assertTrue(assessment.score() < 40);
    }

    @Test
    void fullScanWithoutWhereBlocks() {
        SqlRiskAssessor.RiskAssessment assessment = assessor.assess("SELECT * FROM orders");
        assertTrue(assessment.score() >= 40);
        assertTrue(assessment.risks().stream().anyMatch(r -> r.code().equals("FULL_SCAN")));
        assertTrue(assessment.risks().stream().anyMatch(r -> r.code().equals("SELECT_STAR")));
    }

    @Test
    void selectStarAloneIsLowRiskNotBlocked() {
        SqlRiskAssessor.RiskAssessment assessment = assessor.assess("SELECT * FROM orders WHERE id = 5");
        assertFalse(assessment.blocked());
        assertEquals(15, assessment.score());
        assertTrue(assessment.risks().stream().anyMatch(r -> r.code().equals("SELECT_STAR")));
    }

    @Test
    void aggregateOnlyQueryWithoutWhereIsNotFullScan() {
        SqlRiskAssessor.RiskAssessment assessment = assessor.assess("SELECT COUNT(*) FROM orders");
        assertTrue(assessment.risks().stream().noneMatch(r -> r.code().equals("FULL_SCAN")));
    }

    @Test
    void leadingWildcardAndFunctionOnColumnAreDetected() {
        SqlRiskAssessor.RiskAssessment assessment =
                assessor.assess("SELECT * FROM customers WHERE name LIKE '%x' AND LOWER(city) = 'a'");
        assertTrue(assessment.risks().stream().anyMatch(r -> r.code().equals("LEADING_WILDCARD")));
        assertTrue(assessment.risks().stream().anyMatch(r -> r.code().equals("FUNCTION_ON_COLUMN")));
    }

    @Test
    void scoreIsCappedAtOneHundred() {
        assertTrue(assessor.assess("SELECT * FROM a, b WHERE x LIKE '%z'").score() <= 100);
    }
}
