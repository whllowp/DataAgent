package com.dataagent;

import com.dataagent.bootstrap.DemoDataInitializer;
import com.dataagent.domain.Datasource;
import com.dataagent.domain.QueryExecution;
import com.dataagent.repository.DatasourceRepository;
import com.dataagent.repository.KnowledgeEntryRepository;
import com.dataagent.repository.QueryExecutionRepository;
import com.dataagent.service.HistoryService;
import com.dataagent.service.JdbcQueryService;
import com.dataagent.service.OptimizationService;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.HistoryDtos;
import com.dataagent.web.dto.OptimizeDtos;
import com.dataagent.web.dto.SqlDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dataagent_test_meta;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "dataagent.demo.enabled=true",
        "dataagent.security.api-token=test-token",
        "logging.level.com.dataagent=WARN"
})
@AutoConfigureMockMvc
class DataAgentIntegrationTest {

    @Autowired
    private DemoDataInitializer demoDataInitializer;
    @Autowired
    private DatasourceRepository datasources;
    @Autowired
    private QueryExecutionRepository executions;
    @Autowired
    private KnowledgeEntryRepository knowledge;
    @Autowired
    private JdbcQueryService queries;
    @Autowired
    private HistoryService history;
    @Autowired
    private OptimizationService optimizer;
    @Autowired
    private MockMvc mockMvc;

    private Datasource demo;

    @BeforeEach
    void setUp() throws Exception {
        demoDataInitializer.run(null);
        demo = datasources.findByName(DemoDataInitializer.DEMO_NAME).orElseThrow();
    }

    @Test
    void executesReadOnlyQueryAndCapturesPlan() {
        SqlDtos.QueryResult result = queries.execute(demo.getId(),
                "SELECT city, COUNT(*) AS customers FROM customers GROUP BY city", 5, 10, true,
                "test", "cities", "test");
        assertEquals("SUCCESS", result.status());
        assertFalse(result.columns().isEmpty());
        assertEquals(5, result.rowCount());
        assertTrue(result.truncated());
        assertNotNull(result.plan());
        assertFalse(result.plan().raw().isBlank());
    }

    @Test
    void rejectsWriteSql() {
        assertThrows(ApiException.class, () -> queries.execute(demo.getId(),
                "DELETE FROM customers", 10, 5, false, "test", null, null));
    }

    @Test
    void solvesQueryAndCreatesKnowledgeEntry() {
        long before = knowledge.count();
        SqlDtos.QueryResult result = queries.execute(demo.getId(),
                "SELECT * FROM customers WHERE LOWER(name) LIKE '%客户-01%'", 20, 10, false,
                "test", "customer search", "search");
        HistoryDtos.SolveResult solved = history.markSolved(result.queryId(),
                new HistoryDtos.SolveRequest("客户模糊搜索优化", "搜索慢", "前置通配符导致全表扫描",
                        "改为前缀匹配", "SELECT id, name FROM customers WHERE name LIKE '客户-01%'",
                        "search,slow", 80L), "tester");
        assertNotNull(solved.knowledgeId());
        assertTrue(knowledge.count() >= before);
        QueryExecution execution = executions.findById(result.queryId()).orElseThrow();
        assertTrue(execution.isSolved());
        assertEquals(solved.knowledgeId(), execution.getKnowledgeId());
    }

    @Test
    void optimizerBuildsFindingsAndPrompt() {
        OptimizeDtos.AnalysisView analysis = optimizer.analyze(demo.getId(),
                "SELECT * FROM customers WHERE LOWER(name) LIKE '%abc' ORDER BY created_at", true);
        assertTrue(analysis.findings().stream().anyMatch(f -> f.code().equals("SELECT_STAR")));
        assertTrue(analysis.promptContext().contains("dataagent_explain_sql"));
        assertTrue(analysis.promptContext().contains("只读"));
    }

    @Test
    void apiRequiresToken() throws Exception {
        mockMvc.perform(get("/api/datasources")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/datasources").header("X-API-Key", "test-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
