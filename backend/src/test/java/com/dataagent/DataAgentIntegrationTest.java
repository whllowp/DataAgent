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
import com.dataagent.service.OpsService;
import com.dataagent.service.ApiKeyService;
import com.dataagent.service.AuditExportService;
import com.dataagent.service.BenchmarkService;
import com.dataagent.service.EtlService;
import com.dataagent.service.FieldMappingParser;
import com.dataagent.service.LineageMaintenanceService;
import com.dataagent.service.OpenLineageService;
import com.dataagent.service.AuditService;
import com.dataagent.service.FeedbackService;
import com.dataagent.service.LineageService;
import com.dataagent.service.MemoryCandidateService;
import com.dataagent.service.MemoryService;
import com.dataagent.service.PiiService;
import com.dataagent.service.QueryResultCache;
import com.dataagent.service.SemanticService;
import com.dataagent.service.SemanticSyncService;
import com.dataagent.service.SqlAssetService;
import com.dataagent.service.VerifiedQueryService;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.ApiKeyDtos;
import com.dataagent.web.dto.AuditDtos;
import com.dataagent.web.dto.BenchmarkDtos;
import com.dataagent.web.dto.DolphinSchedulerDtos;
import com.dataagent.web.dto.EtlDtos;
import com.dataagent.web.dto.FeedbackDtos;
import com.dataagent.web.dto.HistoryDtos;
import com.dataagent.web.dto.MemoryCandidateDtos;
import com.dataagent.web.dto.MemoryDtos;
import com.dataagent.web.dto.OpenLineageDtos;
import com.dataagent.web.dto.OptimizeDtos;
import com.dataagent.web.dto.OpsDtos;
import com.dataagent.web.dto.SemanticDtos;
import com.dataagent.web.dto.SqlAssetDtos;
import com.dataagent.web.dto.SqlDtos;
import com.dataagent.web.dto.VerifiedQueryDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private SqlAssetService sqlAssets;
    @Autowired
    private SemanticService semantic;
    @Autowired
    private SemanticSyncService semanticSync;
    @Autowired
    private OpsService ops;
    @Autowired
    private BenchmarkService benchmarks;
    @Autowired
    private VerifiedQueryService verifiedQueries;
    @Autowired
    private MemoryService memories;
    @Autowired
    private MemoryCandidateService memoryCandidates;
    @Autowired
    private FeedbackService feedbacks;
    @Autowired
    private QueryResultCache queryCache;
    @Autowired
    private LineageService lineage;
    @Autowired
    private ApiKeyService apiKeys;
    @Autowired
    private EtlService etl;
    @Autowired
    private FieldMappingParser fieldParser;
    @Autowired
    private LineageMaintenanceService lineageMaintenance;
    @Autowired
    private OpenLineageService openLineage;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private PiiService pii;
    @Autowired
    private AuditExportService auditExport;
    @Autowired
    private AuditService audit;
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
    void cachesSqlAssetWithBusinessProblem() {
        String problem = "大促期间按渠道拆解 GMV，定位渠道下滑原因";
        SqlDtos.QueryResult result = queries.execute(demo.getId(),
                "SELECT channel, SUM(amount) AS gmv FROM sales_orders WHERE status = 'PAID' GROUP BY channel",
                10, 10, false, "test", problem, "GMV,渠道");
        assertEquals("SUCCESS", result.status());

        SqlAssetDtos.AssetPage page = sqlAssets.search("大促期间按渠道拆解", null, demo.getId(), 0, 20);
        assertTrue(page.items().stream().anyMatch(asset -> problem.equals(asset.businessProblem())));
        assertTrue(page.items().stream().anyMatch(asset -> asset.executionCount() >= 1));
    }

    @Test
    void semanticContextAndDesignUseBusinessKnowledge() {
        SemanticDtos.SemanticContext context = semantic.context("GMV 成交额", "销售");
        assertTrue(context.metrics().stream().anyMatch(metric -> "gmv".equals(metric.name())));
        assertTrue(context.entities().stream().anyMatch(entity -> "sales_order".equals(entity.name())));

        SemanticDtos.DesignView design = semantic.design(
                new SemanticDtos.DesignRequest("新增按城市和渠道拆解的 GMV 看板", "销售", demo.getId()));
        assertFalse(design.designAngles().isEmpty());
        assertTrue(design.promptContext().contains("业务目标"));
        assertTrue(design.promptContext().contains("待确认"));
    }

    @Test
    void semanticSyncLinksKnowledgeAndAssetsAndIsIdempotent() {
        sqlAssets.saveDraft(new SqlAssetDtos.SaveRequest(
                "SELECT city, SUM(amount) FROM sales_orders WHERE status = 'PAID' GROUP BY city",
                "按城市分析 GMV 与订单量", "销售", "GMV,城市", demo.getId(), "tester"), "tester");

        SemanticDtos.SyncRunView first = semanticSync.sync("TEST");
        assertEquals("SUCCESS", first.status());
        assertTrue(first.createdLinks() + first.updatedLinks() > 0);
        long firstCount = semantic.overview().links();
        assertTrue(firstCount > 0);

        SemanticDtos.SyncRunView second = semanticSync.sync("TEST");
        assertEquals("SUCCESS", second.status());
        assertEquals(firstCount, semantic.overview().links());
    }

    @Test
    void opsOverviewAndSreBenchmarksAreAvailable() {
        OpsDtos.OpsOverview overview = ops.overview();
        assertNotNull(overview.status());
        assertTrue(overview.benchmarks().total() >= 3);
        assertFalse(overview.pools().isEmpty());

        BenchmarkDtos.RunAllResult run = benchmarks.runAll();
        assertTrue(run.attempted() >= 3);
        assertEquals(0, run.failed());
        OpsDtos.OpsOverview after = ops.overview();
        assertTrue(after.benchmarks().failedLastRun() == 0);
        assertNotNull(after.prometheusPath());
    }

    @Test
    void semanticContextIncludesOntologyAndVerifiedQueries() {
        SemanticDtos.SemanticContext context = semantic.context("GMV 城市", "销售");
        assertFalse(context.relationships().isEmpty());
        assertFalse(context.verifiedQueries().isEmpty());
        assertTrue(context.verifiedQueries().stream()
                .anyMatch(item -> item.question().contains("GMV")));
    }

    @Test
    void validatesVerifiedQuery() {
        VerifiedQueryDtos.View query = verifiedQueries.list("GMV").stream().findFirst().orElseThrow();
        VerifiedQueryDtos.ValidateResult result = verifiedQueries.validate(query.id());
        assertTrue(result.success(), result.message());
        assertTrue(result.rowCount() > 0);
        assertEquals("SUCCESS", verifiedQueries.get(query.id()).lastValidationStatus());
    }

    @Test
    void memoryUpsertsReinforcesAndPromotesKnowledge() {
        String key = "test.memory." + System.nanoTime();
        MemoryDtos.CaptureResult first = memories.capture(new MemoryDtos.CaptureRequest(
                "GLOBAL", null, "PREFERENCE", key, "默认按支付成功订单统计 GMV",
                "USER", 70, 50, "test", "tester", null, false, null, null));
        MemoryDtos.CaptureResult second = memories.capture(new MemoryDtos.CaptureRequest(
                "GLOBAL", null, "PREFERENCE", key, "默认按支付成功订单统计 GMV，日期半开区间",
                "USER", 80, 60, "test", "tester", null, false, null, null));
        assertEquals(first.memory().id(), second.memory().id());
        assertEquals(2, second.memory().reinforcementCount());

        String promoteKey = "test.memory.promote." + System.nanoTime();
        MemoryDtos.CaptureResult promoted = memories.capture(new MemoryDtos.CaptureRequest(
                "GLOBAL", null, "DECISION", promoteKey, "客户数按客户主键去重",
                "USER", 85, 70, "test", "tester", null, true,
                "测试长期记忆知识", "SELECT COUNT(*) FROM customers"));
        assertNotNull(promoted.knowledge());
        assertEquals(promoted.knowledge().id(), promoted.memory().knowledgeId());
    }

    @Test
    void semanticContextIncludesLongTermMemory() {
        assertFalse(semantic.context("GMV 城市", "销售").memories().isEmpty());
        assertTrue(memories.overview().total() >= 4);
    }

    @Test
    void extractsConversationCandidateAndApprovesIt() {
        MemoryCandidateDtos.ExtractResult extracted = memoryCandidates.extract(
                new MemoryCandidateDtos.ExtractRequest("test-session-" + System.nanoTime(), null, "销售",
                        java.util.List.of(new MemoryCandidateDtos.Message("user",
                                "以后 GMV 默认按支付成功订单统计，日期使用半开区间。"))));
        assertTrue(extracted.extracted() >= 1);
        MemoryCandidateDtos.View candidate = extracted.candidates().get(0);
        assertEquals("PENDING", candidate.status());
        assertTrue(candidate.content().contains("默认"));

        MemoryCandidateDtos.ApproveResult approved = memoryCandidates.approve(candidate.id(),
                new MemoryCandidateDtos.ApproveRequest(false, false, null, null, "tester"));
        assertTrue(approved.approved());
        assertNotNull(approved.memory());
        assertEquals("APPROVED", approved.candidate().status());
    }

    @Test
    void detectsConflictingMemoryDefinition() {
        String key = "test.conflict." + System.nanoTime();
        memories.capture(new MemoryDtos.CaptureRequest(
                "GLOBAL", null, "DECISION", key, "渠道为空必须归入 UNKNOWN",
                "USER", 80, 70, "test", "tester", null, false, null, null));
        MemoryDtos.CaptureResult conflict = memories.capture(new MemoryDtos.CaptureRequest(
                "GLOBAL", null, "DECISION", key, "渠道为空不要归入 UNKNOWN",
                "USER", 80, 70, "test", "tester", null, false, null, null));
        assertFalse(conflict.conflicts().isEmpty());
    }

    @Test
    void feedbackCreatesMemoryAndDraftVerifiedQuery() {
        FeedbackDtos.Result result = feedbacks.submit(new FeedbackDtos.Request(
                "feedback-session", "按城市统计 GMV", "SELECT city FROM sales_orders",
                "SELECT city, SUM(amount) FROM sales_orders GROUP BY city",
                "按城市统计 GMV 时必须过滤 PAID", 5, "用户纠错",
                demo.getId(), null, null, "销售", "tester"));
        assertNotNull(result.memory());
        assertNotNull(result.verifiedQuery());
        assertEquals("DRAFT", result.verifiedQuery().status());
        assertNotNull(result.feedback().verifiedQueryId());
    }

    @Test
    void cachesRepeatedQueryAndAuditsIt() {
        String alias = "cache_probe_" + System.nanoTime();
        String sql = "SELECT COUNT(*) AS " + alias + " FROM customers";
        SqlDtos.QueryResult first = queries.execute(demo.getId(), sql, 100, null, false,
                "tester", "cache probe", "test");
        SqlDtos.QueryResult second = queries.execute(demo.getId(), sql, 100, null, false,
                "tester", "cache probe", "test");
        assertFalse(first.cacheHit());
        assertTrue(second.cacheHit());
        assertEquals(first.rowCount(), second.rowCount());

        audit.record("TEST_AUDIT", "SUCCESS", "TEST", null, demo.getId(), "probe");
        assertFalse(audit.search("system", "TEST_AUDIT", null, null, null, null, 10, 0).isEmpty());
        assertNotNull(ops.overview().cache());
        assertNotNull(ops.overview().audit());
    }

    @Test
    void extractsLineageAndImpact() {
        var tables = lineage.extractTables(
                "WITH x AS (SELECT * FROM sales_orders) SELECT * FROM x JOIN customers c ON c.id = x.customer_id");
        assertTrue(tables.contains("SALES_ORDERS"));
        assertTrue(tables.contains("CUSTOMERS"));
        assertFalse(tables.contains("X"));

        assertFalse(lineage.impact("sales_orders").assets().isEmpty());
        assertFalse(lineage.impact("sales_orders").semanticEntities().isEmpty());
        assertFalse(lineage.tables().isEmpty());
    }

    @Test
    void scopedApiKeysEnforceRolesAndDatasourceAcl() throws Exception {
        ApiKeyDtos.Created reader = apiKeys.create(new ApiKeyDtos.CreateRequest(
                "read-test", "READ", "default", null, java.util.List.of(demo.getId())));
        mockMvc.perform(get("/api/datasources").header("X-API-Key", reader.key()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sql/execute")
                        .header("X-API-Key", reader.key())
                        .contentType("application/json")
                        .content("{\"datasourceId\":\"" + demo.getId() + "\",\"sql\":\"SELECT 1 AS ok\"}"))
                .andExpect(status().isForbidden());

        ApiKeyDtos.Created executor = apiKeys.create(new ApiKeyDtos.CreateRequest(
                "exec-test", "EXECUTE", "default", null, java.util.List.of(demo.getId())));
        mockMvc.perform(post("/api/sql/execute")
                        .header("X-API-Key", executor.key())
                        .contentType("application/json")
                        .content("{\"datasourceId\":\"" + demo.getId() + "\",\"sql\":\"SELECT 1 AS ok\"}"))
                .andExpect(status().isOk());

        ApiKeyDtos.Created otherTenant = apiKeys.create(new ApiKeyDtos.CreateRequest(
                "other-test", "ADMIN", "other", null, java.util.List.of()));
        mockMvc.perform(get("/api/datasources").header("X-API-Key", otherTenant.key()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}"));
        mockMvc.perform(get("/api/datasources/" + demo.getId()).header("X-API-Key", otherTenant.key()))
                .andExpect(status().isForbidden());
    }

    @Test
    void masksPiiAndExtractsFieldLineage() {
        assertEquals("EMAIL", pii.classify("customer_email", null));
        assertEquals("a***@example.com", pii.mask("EMAIL", "alice@example.com"));

        SqlDtos.QueryResult result = queries.execute(demo.getId(),
                "SELECT 'alice@example.com' AS email, '13812345678' AS mobile", 10, null, false,
                "tester", "pii probe", "test");
        assertTrue(result.piiMaskedColumns().stream().anyMatch(column -> "email".equalsIgnoreCase(column)));
        assertEquals("a***@example.com", result.rows().get(0).get(0));

        var fields = lineage.extractFields(
                "SELECT o.amount, c.city FROM sales_orders o JOIN customers c ON c.id = o.customer_id");
        assertTrue(fields.contains("SALES_ORDERS.AMOUNT"));
        assertTrue(fields.contains("CUSTOMERS.CITY"));
        assertFalse(lineage.fields().isEmpty());
    }

    @Test
    void exportsAuditJsonlAndPurgesByRetention() {
        audit.record("EXPORT_TEST", "SUCCESS", "TEST", null, demo.getId(), "export probe");
        assertFalse(audit.search(null, "EXPORT_TEST", null, null, null, null, 10, 0).isEmpty());
        String jsonl = auditExport.jsonl(java.time.Instant.now().minusSeconds(3600), java.time.Instant.now());
        assertTrue(jsonl.contains("EXPORT_TEST"));
        assertNotNull(auditExport.export(new AuditDtos.ExportRequest(
                java.time.Instant.now().minusSeconds(3600), java.time.Instant.now(), "FILE")));
    }

    @Test
    void importsDolphinSchedulerTopologyAndAnalyzesEndToEndLineage() {
        long base = 9_000_000L + Math.abs(System.nanoTime() % 1_000_000L);
        long first = base;
        long second = base + 1;
        DolphinSchedulerDtos.ProcessDefinition upstream = new DolphinSchedulerDtos.ProcessDefinition(
                first, "etl_ods_to_dwd_" + base, "test", "ONLINE",
                new DolphinSchedulerDtos.Schedule("0 0 2 * * ?", null, "Asia/Shanghai", null, "ONLINE"),
                java.util.List.of(
                        new DolphinSchedulerDtos.TaskDefinition(1L, "extract_ods", "SQL", "SQL",
                                java.util.Map.of("sql", "INSERT OVERWRITE TABLE dwd_etl SELECT * FROM ods_etl"),
                                null, 1, null),
                        new DolphinSchedulerDtos.TaskDefinition(2L, "build_ads", "SQL", "SQL",
                                java.util.Map.of("sql", "INSERT OVERWRITE TABLE ads_etl SELECT * FROM dwd_etl"),
                                null, 2, null)),
                null,
                java.util.List.of(new DolphinSchedulerDtos.TaskRelation(1L, 2L, "ALL_SUCCESS", 0L)),
                null, java.util.List.of());
        DolphinSchedulerDtos.ProcessDefinition downstream = new DolphinSchedulerDtos.ProcessDefinition(
                second, "etl_ads_publish_" + base, "test", "ONLINE",
                new DolphinSchedulerDtos.Schedule("0 30 2 * * ?", null, "Asia/Shanghai", null, "ONLINE"),
                java.util.List.of(new DolphinSchedulerDtos.TaskDefinition(3L, "publish", "SQL", "SQL",
                        java.util.Map.of("sql", "INSERT OVERWRITE TABLE ads_report SELECT * FROM ads_etl"),
                        null, 1, null)),
                null, null, null,
                java.util.List.of(new DolphinSchedulerDtos.WorkflowDependency(
                        first, null, second, null, "ALL_SUCCESS", 0L)));
        EtlDtos.ImportResult imported = etl.importDolphinScheduler(
                new DolphinSchedulerDtos.ImportRequest("default", 1L, "etl-test",
                        java.util.List.of(upstream, downstream)));
        assertEquals(2, imported.workflows());
        assertEquals(3, imported.tasks());
        assertEquals(1, imported.taskDependencies());
        assertEquals(1, imported.workflowDependencies());

        EtlDtos.WorkflowView firstView = etl.list().stream()
                .filter(item -> ("etl_ods_to_dwd_" + base).equals(item.name())).findFirst().orElseThrow();
        EtlDtos.WorkflowGraph graph = etl.graph(firstView.id());
        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertEquals(2, graph.topologicalOrder().size());
        assertTrue(graph.cycles().isEmpty());
        assertFalse(lineageMaintenance.taskFields(etl.tasks(firstView.id()).get(0).id()).isEmpty());

        EtlDtos.TableLineage endToEnd = etl.lineage("ods_etl", "downstream", 10);
        assertTrue(endToEnd.nodes().stream().anyMatch(node -> "DWD_ETL".equals(node.table())));
        assertTrue(endToEnd.nodes().stream().anyMatch(node -> "ADS_ETL".equals(node.table())));
        assertFalse(endToEnd.workflows().isEmpty());

        EtlDtos.WorkflowView secondView = etl.list().stream()
                .filter(item -> ("etl_ads_publish_" + base).equals(item.name())).findFirst().orElseThrow();
        EtlDtos.Analysis analysis = etl.analysis(secondView.id());
        assertFalse(analysis.upstreamWorkflows().isEmpty());
        assertNotNull(analysis.schedule().nextRun());
        assertFalse(lineage.impact("ods_etl").etlTasks().isEmpty());
    }

    @Test
    void mapsEltFieldsAndIngestsOpenLineageColumnLineage() throws Exception {
        var mappings = fieldParser.parse(
                "INSERT INTO dwd_f (id, amount) SELECT s.id AS id, s.amount AS amount FROM src_f s",
                lineage);
        assertTrue(mappings.stream().anyMatch(item -> "DWD_F".equals(item.targetTable())
                && "ID".equals(item.targetColumn()) && "SRC_F".equals(item.sourceTable())
                && "ID".equals(item.sourceColumn())));
        assertTrue(mappings.stream().anyMatch(item -> "AMOUNT".equals(item.targetColumn())
                && "AMOUNT".equals(item.sourceColumn())));

        JsonNode event = objectMapper.readTree("""
                {
                  "eventType": "COMPLETE",
                  "eventTime": "2026-11-11T00:00:00Z",
                  "run": {"runId": "run-ol-1"},
                  "job": {"namespace": "etl", "name": "ol_job"},
                  "inputs": [{"namespace": "hive", "name": "hive.ods_ol"}],
                  "outputs": [{
                    "namespace": "hive",
                    "name": "hive.dwd_ol",
                    "facets": {
                      "columnLineage": {
                        "fields": {
                          "ID": {
                            "inputFields": [{"namespace": "hive", "name": "hive.ods_ol", "field": "ID"}],
                            "transformationType": "IDENTITY"
                          }
                        }
                      }
                    }
                  }]
                }
                """);
        OpenLineageDtos.IngestResult ingested = openLineage.ingest(event);
        assertEquals(1, ingested.tableEdges());
        assertEquals(1, ingested.fieldEdges());
        assertFalse(openLineage.events("ol_job").isEmpty());
        assertFalse(lineageMaintenance.fieldLineage("ODS_OL", "ID").isEmpty());
        assertFalse(lineage.impact("ODS_OL").openLineageJobs().isEmpty());
    }

    @Test
    void openApiContractExposesCorePaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/datasources']").exists())
                .andExpect(jsonPath("$.paths['/api/sql/execute']").exists())
                .andExpect(jsonPath("$.paths['/api/audit']").exists())
                .andExpect(jsonPath("$.paths['/api/lineage/impact']").exists());
    }

    @Test
    void apiRequiresToken() throws Exception {
        mockMvc.perform(get("/api/datasources")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/datasources").header("X-API-Key", "test-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void allowsDshDesktopOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "dsh-app://dsh")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-API-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "dsh-app://dsh"));
    }
}
