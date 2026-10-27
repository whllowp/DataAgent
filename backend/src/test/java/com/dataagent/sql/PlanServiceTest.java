package com.dataagent.sql;

import com.dataagent.domain.DbDialect;
import com.dataagent.web.dto.SqlDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanServiceTest {

    private final PlanService planService = new PlanService(new ObjectMapper());

    @Test
    void extractsPostgresSeqScanInsight() {
        String raw = """
                [{"Plan":{"Node Type":"Seq Scan","Relation Name":"orders","Plan Rows":12000,
                "Total Cost":1500.0,"Plans":[]}}]
                """;
        SqlDtos.PlanView plan = planService.analyze(DbDialect.POSTGRESQL, raw, 3);
        assertTrue(plan.summary().contains("Seq Scan"));
        assertTrue(plan.insights().stream().anyMatch(i -> i.code().equals("PG_SEQ_SCAN")));
        assertTrue(plan.insights().stream().anyMatch(i -> i.code().equals("PG_HIGH_COST")));
    }

    @Test
    void extractsMySqlFullScanInsight() {
        String raw = """
                {"query_block":{"cost_info":{"query_cost":"18.5"},"table":{"table_name":"orders",
                "access_type":"ALL","rows_examined_per_scan":2000,"key":null}}}
                """;
        SqlDtos.PlanView plan = planService.analyze(DbDialect.MYSQL, raw, 1);
        assertTrue(plan.summary().contains("orders"));
        assertTrue(plan.insights().stream().anyMatch(i -> i.code().equals("MYSQL_FULL_SCAN")));
    }

    @Test
    void extractsH2FullScanInsight() {
        String raw = "/* PUBLIC.SALES_ORDERS.tableScan */\nSELECT * FROM SALES_ORDERS";
        SqlDtos.PlanView plan = planService.analyze(DbDialect.H2, raw, 0);
        assertTrue(plan.insights().stream().anyMatch(i -> i.code().equals("H2_FULL_SCAN")));
        assertTrue(plan.summary().contains("SALES_ORDERS"));
    }
}
