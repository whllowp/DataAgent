package com.dataagent.bootstrap;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.Datasource;
import com.dataagent.domain.KnowledgeEntry;
import com.dataagent.domain.QueryExecution;
import com.dataagent.repository.DatasourceRepository;
import com.dataagent.repository.KnowledgeEntryRepository;
import com.dataagent.repository.QueryExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    public static final String DEMO_URL = "jdbc:h2:mem:dataagent_demo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
    public static final String DEMO_NAME = "Demo 销售库";

    private final DataAgentProperties properties;
    private final DatasourceRepository datasources;
    private final QueryExecutionRepository executions;
    private final KnowledgeEntryRepository knowledge;

    public DemoDataInitializer(DataAgentProperties properties, DatasourceRepository datasources,
                               QueryExecutionRepository executions, KnowledgeEntryRepository knowledge) {
        this.properties = properties;
        this.datasources = datasources;
        this.executions = executions;
        this.knowledge = knowledge;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.getDemo().isEnabled()) {
            return;
        }
        createDemoDatabase();
        Datasource demo = datasources.findByName(DEMO_NAME).orElseGet(this::createDatasource);
        if (executions.count() == 0) {
            seedHistoryAndKnowledge(demo);
        }
        log.info("Demo datasource ready: {} ({})", demo.getName(), demo.getId());
    }

    private void createDemoDatabase() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection connection = DriverManager.getConnection(DEMO_URL, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS customers (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            city VARCHAR(40) NOT NULL,
                            segment VARCHAR(20) NOT NULL,
                            created_at DATE NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS products (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            category VARCHAR(40) NOT NULL,
                            price DECIMAL(12, 2) NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS sales_orders (
                            id BIGINT PRIMARY KEY,
                            customer_id BIGINT NOT NULL,
                            product_id BIGINT NOT NULL,
                            order_date DATE NOT NULL,
                            quantity INT NOT NULL,
                            amount DECIMAL(14, 2) NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            channel VARCHAR(20) NOT NULL
                        )
                        """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales_orders(customer_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_sales_date ON sales_orders(order_date)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_sales_status ON sales_orders(status)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_customer_city ON customers(city)");
            }

            if (count(connection, "customers") == 0) {
                seedCustomers(connection);
                seedProducts(connection);
                seedOrders(connection);
            }
        }
    }

    private void seedCustomers(Connection connection) throws Exception {
        String[] cities = {"北京", "上海", "深圳", "杭州", "成都", "武汉", "南京", "西安"};
        String[] segments = {"ENTERPRISE", "SMB", "CONSUMER"};
        String sql = "INSERT INTO customers(id, name, city, segment, created_at) VALUES (?, ?, ?, ?, ?)";
        Random random = new Random(42);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 2_000; i++) {
                statement.setLong(1, i);
                statement.setString(2, "客户-" + String.format("%04d", i));
                statement.setString(3, cities[random.nextInt(cities.length)]);
                statement.setString(4, segments[random.nextInt(segments.length)]);
                statement.setDate(5, java.sql.Date.valueOf(LocalDate.of(2023, 1, 1)
                        .plusDays(random.nextInt(900))));
                statement.addBatch();
                if (i % 500 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void seedProducts(Connection connection) throws Exception {
        String[] categories = {"数据库", "中间件", "云服务", "咨询服务", "培训"};
        String sql = "INSERT INTO products(id, name, category, price) VALUES (?, ?, ?, ?)";
        Random random = new Random(7);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 80; i++) {
                statement.setLong(1, i);
                statement.setString(2, "产品-" + i);
                statement.setString(3, categories[random.nextInt(categories.length)]);
                statement.setBigDecimal(4, BigDecimal.valueOf(100 + random.nextInt(9_900))
                        .setScale(2, RoundingMode.HALF_UP));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void seedOrders(Connection connection) throws Exception {
        String[] statuses = {"PAID", "PAID", "PAID", "SHIPPED", "REFUNDED", "PENDING"};
        String[] channels = {"WEB", "APP", "API", "OFFLINE"};
        String sql = """
                INSERT INTO sales_orders(id, customer_id, product_id, order_date, quantity, amount, status, channel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Random random = new Random(2025);
        LocalDate start = LocalDate.of(2024, 1, 1);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 20_000; i++) {
                long productId = 1 + random.nextInt(80);
                int quantity = 1 + random.nextInt(8);
                BigDecimal amount = BigDecimal.valueOf(100 + random.nextInt(9_900))
                        .multiply(BigDecimal.valueOf(quantity))
                        .setScale(2, RoundingMode.HALF_UP);
                statement.setLong(1, i);
                statement.setLong(2, 1 + random.nextInt(2_000));
                statement.setLong(3, productId);
                statement.setDate(4, java.sql.Date.valueOf(start.plusDays(random.nextInt(730))));
                statement.setInt(5, quantity);
                statement.setBigDecimal(6, amount);
                statement.setString(7, statuses[random.nextInt(statuses.length)]);
                statement.setString(8, channels[random.nextInt(channels.length)]);
                statement.addBatch();
                if (i % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private Datasource createDatasource() {
        Datasource datasource = new Datasource();
        datasource.setName(DEMO_NAME);
        datasource.setDialect("H2");
        datasource.setJdbcUrl(DEMO_URL);
        datasource.setUsername("sa");
        datasource.setPasswordCipher(null);
        datasource.setReadOnly(true);
        datasource.setMaxRows(500);
        datasource.setQueryTimeoutSeconds(15);
        datasource.setDescription("内置演示库：客户、商品、订单，含 2 万行订单数据");
        datasource.setCreatedAt(Instant.now());
        return datasources.save(datasource);
    }

    private void seedHistoryAndKnowledge(Datasource demo) {
        Instant now = Instant.now();
        QueryExecution first = new QueryExecution();
        first.setDatasourceId(demo.getId());
        first.setUserId("demo");
        first.setKind("QUERY");
        first.setSqlText("""
                SELECT c.city, COUNT(*) AS orders, SUM(o.amount) AS gmv
                FROM sales_orders o
                JOIN customers c ON c.id = o.customer_id
                WHERE YEAR(o.order_date) = 2025
                GROUP BY c.city
                ORDER BY gmv DESC
                """);
        first.setSqlFingerprint("demo-fingerprint-01");
        first.setStatus("SUCCESS");
        first.setDurationMs(1_860);
        first.setFetchMs(22);
        first.setRowCount(8);
        first.setProblem("大促期间按城市统计 GMV 变慢，页面等待超过 1.8 秒");
        first.setTags("慢查询,聚合,日期函数");
        first.setPlanRaw("""
                /* PUBLIC.SALES_ORDERS.tableScan */
                /* PUBLIC.CUSTOMERS.tableScan */
                /* GROUP BY */
                /* SORT */
                """);
        first.setPlanFormat("TEXT");
        first.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
        first.setSolved(true);

        QueryExecution second = new QueryExecution();
        second.setDatasourceId(demo.getId());
        second.setUserId("demo");
        second.setKind("QUERY");
        second.setSqlText("SELECT * FROM sales_orders WHERE status = 'PAID' ORDER BY order_date DESC LIMIT 500");
        second.setSqlFingerprint("demo-fingerprint-02");
        second.setStatus("SUCCESS");
        second.setDurationMs(420);
        second.setFetchMs(18);
        second.setRowCount(500);
        second.setProblem("订单列表接口偶尔变慢");
        second.setTags("列表查询,SELECT_STAR");
        second.setPlanRaw("/* PUBLIC.SALES_ORDERS.indexScan idx_sales_status */");
        second.setPlanFormat("TEXT");
        second.setCreatedAt(now.minus(5, ChronoUnit.HOURS));

        QueryExecution third = new QueryExecution();
        third.setDatasourceId(demo.getId());
        third.setUserId("demo");
        third.setKind("QUERY");
        third.setSqlText("""
                SELECT *
                FROM customers
                WHERE LOWER(name) LIKE '%客户-01%'
                """);
        third.setSqlFingerprint("demo-fingerprint-03");
        third.setStatus("SUCCESS");
        third.setDurationMs(980);
        third.setFetchMs(40);
        third.setRowCount(500);
        third.setProblem("客户名称模糊搜索慢");
        third.setTags("模糊搜索,函数索引");
        third.setPlanRaw("/* PUBLIC.CUSTOMERS.tableScan */");
        third.setPlanFormat("TEXT");
        third.setCreatedAt(now.minus(1, ChronoUnit.DAYS));

        executions.save(first);
        executions.save(second);
        executions.save(third);

        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setTitle("按城市统计 GMV：YEAR(order_date) 导致索引失效");
        entry.setProblem("大促期间按城市统计 GMV 变慢，页面等待超过 1.8 秒");
        entry.setSqlText(first.getSqlText());
        entry.setOptimizedSql("""
                SELECT c.city, COUNT(*) AS orders, SUM(o.amount) AS gmv
                FROM sales_orders o
                JOIN customers c ON c.id = o.customer_id
                WHERE o.order_date >= DATE '2025-01-01'
                  AND o.order_date <  DATE '2026-01-01'
                GROUP BY c.city
                ORDER BY gmv DESC
                """);
        entry.setDialect("H2");
        entry.setTags("慢查询,聚合,日期函数,索引失效");
        entry.setPlanSummary("原计划对 SALES_ORDERS 全表扫描后做 GROUP BY + SORT；谓词作用在 YEAR(order_date) 上，无法利用 order_date 索引。");
        entry.setRootCause("WHERE 条件对索引列使用 YEAR() 函数，优化器无法做索引范围扫描。");
        entry.setOptimizationSteps("1. 将 YEAR(o.order_date)=2025 改写为半开区间 [2025-01-01, 2026-01-01)。\n2. 确认 order_date 上有索引，并让谓词直接作用于该列。\n3. 用 EXPLAIN 对比全表扫描是否消失，再以真实参数复测。");
        entry.setBeforeDurationMs(1_860L);
        entry.setAfterDurationMs(380L);
        entry.setAuthor("demo");
        entry.setSourceQueryId(first.getId());
        entry.setUsageCount(3);
        knowledge.save(entry);

        first.setKnowledgeId(entry.getId());
        executions.save(first);

        KnowledgeEntry secondEntry = new KnowledgeEntry();
        secondEntry.setTitle("订单状态筛选 + 日期排序：组合索引减少 filesort");
        secondEntry.setProblem("订单列表接口偶尔变慢");
        secondEntry.setSqlText(second.getSqlText());
        secondEntry.setOptimizedSql("""
                SELECT id, customer_id, product_id, order_date, quantity, amount, status, channel
                FROM sales_orders
                WHERE status = 'PAID'
                ORDER BY order_date DESC
                LIMIT 500
                """);
        secondEntry.setDialect("H2");
        secondEntry.setTags("列表查询,组合索引,SELECT_STAR");
        secondEntry.setPlanSummary("去掉 SELECT * 后可利用覆盖索引；status + order_date 组合索引可同时服务过滤和排序。");
        secondEntry.setRootCause("SELECT * 扩大读取列，单列 status 索引无法覆盖排序。");
        secondEntry.setOptimizationSteps("1. 只 SELECT 必要列。\n2. 评估 (status, order_date) 组合索引。\n3. 用 EXPLAIN 与基准对比验证排序是否消除。");
        secondEntry.setBeforeDurationMs(420L);
        secondEntry.setAfterDurationMs(210L);
        secondEntry.setAuthor("demo");
        secondEntry.setSourceQueryId(second.getId());
        knowledge.save(secondEntry);

        log.info("Seeded demo history and knowledge base");
    }

    private long count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
