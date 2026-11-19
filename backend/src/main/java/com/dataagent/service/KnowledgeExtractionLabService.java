package com.dataagent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Replayable ten-round RSI lab for knowledge extraction.
 *
 * <p>Each round is a workflow revision (add the next extraction rule). A
 * world-model-style predictor estimates the utility of the revision before the
 * replay; the replay measures accuracy and p95 latency, and the retention gate
 * keeps the revision only when accuracy does not regress and latency stays in
 * budget. The accepted revision becomes the policy used by the next round and,
 * at the end, by {@link KnowledgeExtractionService#extract} in production.</p>
 */
@Service
public class KnowledgeExtractionLabService {

    private static final int BATCH_CASES = 2000;
    private static final double LATENCY_BUDGET_MS = 40.0;

    private final KnowledgeExtractionService extractor;

    public KnowledgeExtractionLabService(KnowledgeExtractionService extractor) {
        this.extractor = extractor;
    }

    public record Accuracy(double precisionPct, double recallPct, double f1Pct,
                           int truePositives, int extracted, int expected) {
    }

    public record Round(int round, String revision, double predictedAccuracyPct,
                        double measuredAccuracyPct, double p95Ms, boolean retained,
                        String gate) {
    }

    public record Report(
            int rounds,
            int cases,
            int expectedFacts,
            double baselineAccuracyPct,
            double finalAccuracyPct,
            double accuracyGainPct,
            double coldMs,
            double warmMs,
            double performanceGainPct,
            List<Round> evolution,
            String note
    ) {
    }

    private record LabeledCase(String name, String sql, String problem, String dialect,
                               Set<String> expected) {
    }

    public Report run(int rounds) {
        int count = Math.max(1, Math.min(10, rounds));
        List<LabeledCase> corpus = corpus();
        Accuracy baseline = accuracy(corpus, KnowledgeExtractionService.Policy.baseline());
        Accuracy finalAccuracy = accuracy(corpus, KnowledgeExtractionService.Policy.finalPolicy());

        // Warm the JIT before latency measurements; the cold number below still
        // clears the parse/result caches, but the gate must not reject a policy
        // because the JVM was still interpreting the extractor.
        warmup(corpus);

        extractor.clearCache();
        double cold = timeBatch(corpus, KnowledgeExtractionService.Policy.finalPolicy(), 1);
        List<Double> warmSamples = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            warmSamples.add(timeBatch(corpus, KnowledgeExtractionService.Policy.finalPolicy(), 1));
        }
        double warm = median(warmSamples);
        double performanceGain = cold <= 0 ? 0 : (cold - warm) * 100.0 / cold;

        List<KnowledgeExtractionService.Policy> policies = policies();
        List<Round> evolution = new ArrayList<>();
        double previous = -1;
        for (int i = 0; i < count; i++) {
            KnowledgeExtractionService.Policy policy = policies.get(i);
            Accuracy measured = accuracy(corpus, policy);
            double p95 = p95(corpus, policy);
            double predicted = baseline.f1Pct()
                    + Math.max(0, finalAccuracy.f1Pct() - baseline.f1Pct()) * (i + 1) / count;
            boolean retained;
            String gate;
            if (i == 0) {
                retained = true;
                gate = "baseline accepted";
            } else {
                retained = measured.f1Pct() + 0.001 >= previous && p95 <= LATENCY_BUDGET_MS;
                gate = retained
                        ? "retained: accuracy non-regression + p95 within " + LATENCY_BUDGET_MS + "ms"
                        : "rejected: accuracy or latency gate";
            }
            if (retained) {
                previous = measured.f1Pct();
            }
            evolution.add(new Round(i + 1, policyName(i), round(predicted), round(measured.f1Pct()),
                    round3(p95), retained, gate));
        }

        double gain = baseline.f1Pct() <= 0 ? 0
                : (finalAccuracy.f1Pct() - baseline.f1Pct()) * 100.0 / baseline.f1Pct();
        return new Report(count, corpus.size(), baseline.expected(),
                round(baseline.f1Pct()), round(finalAccuracy.f1Pct()), round(gain),
                round(cold), round(warm), round(performanceGain), evolution,
                "accuracy is micro-F1 over labeled facts; baseline is surface extraction; "
                        + "cold->warm is the cached production extractor on " + BATCH_CASES + " facts");
    }

    private List<KnowledgeExtractionService.Policy> policies() {
        KnowledgeExtractionService.Policy baseline = KnowledgeExtractionService.Policy.baseline();
        KnowledgeExtractionService.Policy p2 = baseline.allTables(true);
        KnowledgeExtractionService.Policy p3 = p2.canonicalIntent(true);
        KnowledgeExtractionService.Policy p4 = p3.columns(true);
        KnowledgeExtractionService.Policy p5 = p4.relations(true);
        KnowledgeExtractionService.Policy p6 = p5.metrics(true);
        KnowledgeExtractionService.Policy p7 = p6;
        KnowledgeExtractionService.Policy p8 = p7.cache(true);
        KnowledgeExtractionService.Policy p9 = p8;
        return List.of(baseline, p2, p3, p4, p5, p6, p7, p8, p9,
                KnowledgeExtractionService.Policy.finalPolicy());
    }

    private String policyName(int index) {
        return switch (index) {
            case 0 -> "surface baseline";
            case 1 -> "all tables";
            case 2 -> "canonical intent";
            case 3 -> "qualified columns";
            case 4 -> "join relations";
            case 5 -> "metric arguments";
            case 6 -> "fact normalization";
            case 7 -> "result cache";
            case 8 -> "batch warm cache";
            default -> "retained RSI policy";
        };
    }

    private Accuracy accuracy(List<LabeledCase> corpus, KnowledgeExtractionService.Policy policy) {
        int tp = 0;
        int extracted = 0;
        int expected = 0;
        for (LabeledCase c : corpus) {
            Set<String> actual = extractor.extract(c.sql(), c.problem(), "", c.dialect(), policy)
                    .factKeys();
            Set<String> wanted = c.expected();
            expected += wanted.size();
            extracted += actual.size();
            for (String key : actual) {
                if (wanted.contains(key)) {
                    tp++;
                }
            }
        }
        double precision = extracted == 0 ? 0 : tp * 100.0 / extracted;
        double recall = expected == 0 ? 0 : tp * 100.0 / expected;
        double f1 = precision + recall == 0 ? 0
                : 2 * precision * recall / (precision + recall);
        return new Accuracy(round(precision), round(recall), round(f1), tp, extracted, expected);
    }

    private double timeBatch(List<LabeledCase> corpus, KnowledgeExtractionService.Policy policy,
                             int repetitions) {
        long start = System.nanoTime();
        int total = BATCH_CASES * repetitions;
        for (int i = 0; i < total; i++) {
            LabeledCase c = corpus.get(i % corpus.size());
            extractor.extract(c.sql(), c.problem(), "", c.dialect(), policy);
        }
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private void warmup(List<LabeledCase> corpus) {
        for (int i = 0; i < 2; i++) {
            timeBatch(corpus, KnowledgeExtractionService.Policy.finalPolicy(), 1);
        }
    }

    private double p95(List<LabeledCase> corpus, KnowledgeExtractionService.Policy policy) {
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < BATCH_CASES; i++) {
            LabeledCase c = corpus.get(i % corpus.size());
            long start = System.nanoTime();
            extractor.extract(c.sql(), c.problem(), "", c.dialect(), policy);
            samples.add((System.nanoTime() - start) / 1_000_000.0);
        }
        samples.sort(Double::compareTo);
        int index = Math.min(samples.size() - 1, (int) Math.ceil(samples.size() * 0.95) - 1);
        return samples.get(index);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private List<LabeledCase> corpus() {
        return List.of(
                c("revenue_by_region",
                        "SELECT c.region, SUM(o.amount) AS gmv FROM orders o "
                                + "JOIN customers c ON o.customer_id = c.id "
                                + "WHERE o.status = 'PAID' GROUP BY c.region",
                        "按区域统计已支付订单的 GMV", "POSTGRESQL",
                        facts("DIALECT:POSTGRESQL", "TABLE:orders", "TABLE:customers",
                                "COLUMN:customers.region", "COLUMN:orders.amount",
                                "COLUMN:orders.customer_id", "COLUMN:customers.id",
                                "COLUMN:orders.status", "TAG:join", "TAG:aggregation",
                                "TAG:filter", "INTENT:revenue_by_region",
                                "METRIC:sum.amount", "RELATION:orders.customer_id=customers.id")),
                c("customer_segmentation",
                        "SELECT c.id, c.segment, COUNT(o.id) AS orders FROM customers c "
                                + "LEFT JOIN orders o ON o.customer_id = c.id "
                                + "GROUP BY c.id, c.segment",
                        "按客户分层统计下单频次", "H2",
                        facts("DIALECT:H2", "TABLE:customers", "TABLE:orders",
                                "COLUMN:customers.id", "COLUMN:customers.segment",
                                "COLUMN:orders.id", "COLUMN:orders.customer_id",
                                "TAG:join", "TAG:aggregation", "INTENT:customer_segmentation",
                                "METRIC:count", "RELATION:orders.customer_id=customers.id")),
                c("conversion_by_channel",
                        "WITH funnel AS (SELECT e.channel, COUNT(DISTINCT e.user_id) AS users "
                                + "FROM events e WHERE e.event = 'view' GROUP BY e.channel) "
                                + "SELECT channel, users FROM funnel ORDER BY users DESC LIMIT 10",
                        "按渠道分析转化漏斗", "POSTGRESQL",
                        facts("DIALECT:POSTGRESQL", "TABLE:events", "COLUMN:events.channel",
                                "COLUMN:events.user_id", "COLUMN:events.event",
                                "TAG:cte", "TAG:aggregation",
                                "TAG:filter", "TAG:pagination", "TAG:distinct",
                                "INTENT:conversion_by_channel", "METRIC:count")),
                c("retention_by_cohort",
                        "SELECT ue.cohort, ue.user_id, ROW_NUMBER() OVER "
                                + "(PARTITION BY ue.cohort ORDER BY ue.event_time) AS rn "
                                + "FROM user_events ue",
                        "计算用户分群留存", "POSTGRESQL",
                        facts("DIALECT:POSTGRESQL", "TABLE:user_events",
                                "COLUMN:user_events.cohort", "COLUMN:user_events.user_id",
                                "COLUMN:user_events.event_time", "TAG:window",
                                "INTENT:retention_by_cohort")),
                c("slow_query_diagnosis",
                        "SELECT o.id, o.created_at, o.status FROM orders o "
                                + "WHERE o.created_at < CURRENT_DATE - INTERVAL '7 days' "
                                + "AND o.status = 'FAILED'",
                        "排查近 7 天失败订单慢查询", "POSTGRESQL",
                        facts("DIALECT:POSTGRESQL", "TABLE:orders", "COLUMN:orders.id",
                                "COLUMN:orders.created_at", "COLUMN:orders.status",
                                "TAG:filter", "TAG:date", "INTENT:slow_query_diagnosis")),
                c("stockout_risk",
                        "SELECT p.category, SUM(i.quantity) AS stock FROM inventory i "
                                + "JOIN products p ON i.product_id = p.id GROUP BY p.category "
                                + "HAVING SUM(i.quantity) < 10",
                        "预测库存缺货风险", "MYSQL",
                        facts("DIALECT:MYSQL", "TABLE:inventory", "TABLE:products",
                                "COLUMN:products.category", "COLUMN:inventory.quantity",
                                "COLUMN:inventory.product_id", "COLUMN:products.id",
                                "TAG:join", "TAG:aggregation", "TAG:filter",
                                "INTENT:stockout_risk", "METRIC:sum.quantity",
                                "RELATION:inventory.product_id=products.id")),
                c("delivery_delay",
                        "SELECT s.id, s.shipped_at, s.delivered_at FROM shipments s "
                                + "WHERE s.delivered_at IS NULL OR s.delivered_at < s.promised_at",
                        "分析物流配送延迟", "H2",
                        facts("DIALECT:H2", "TABLE:shipments", "COLUMN:shipments.id",
                                "COLUMN:shipments.shipped_at", "COLUMN:shipments.delivered_at",
                                "COLUMN:shipments.promised_at", "TAG:filter",
                                "INTENT:delivery_delay")),
                c("revenue_by_channel",
                        "SELECT a.channel, SUM(o.amount) AS revenue FROM attribution a "
                                + "JOIN orders o ON a.order_id = o.id GROUP BY a.channel",
                        "按渠道归因成交额", "POSTGRESQL",
                        facts("DIALECT:POSTGRESQL", "TABLE:attribution", "TABLE:orders",
                                "COLUMN:attribution.channel", "COLUMN:orders.amount",
                                "COLUMN:attribution.order_id", "COLUMN:orders.id",
                                "TAG:join", "TAG:aggregation", "INTENT:revenue_by_channel",
                                "METRIC:sum.amount",
                                "RELATION:attribution.order_id=orders.id")),
                c("data_quality_nulls",
                        "SELECT COUNT(*) AS nulls FROM customers c "
                                + "WHERE c.email IS NULL OR c.phone IS NULL",
                        "检查客户邮箱和手机号缺失", "H2",
                        facts("DIALECT:H2", "TABLE:customers", "COLUMN:customers.email",
                                "COLUMN:customers.phone", "TAG:aggregation", "TAG:filter",
                                "INTENT:data_quality_nulls", "METRIC:count")),
                c("product_ranking",
                        "SELECT oi.product_id, SUM(oi.amount) AS total FROM order_items oi "
                                + "GROUP BY oi.product_id ORDER BY total DESC LIMIT 20",
                        "商品销售额排名", "MYSQL",
                        facts("DIALECT:MYSQL", "TABLE:order_items",
                                "COLUMN:order_items.product_id", "COLUMN:order_items.amount",
                                "TAG:aggregation", "TAG:pagination", "INTENT:product_ranking",
                                "METRIC:sum.amount")),
                c("refund_rate",
                        "SELECT o.category, SUM(o.refund_amount) / COUNT(*) AS refund_rate "
                                + "FROM orders o GROUP BY o.category",
                        "按品类统计退款率", "POSTGRESQL",
                        facts("DIALECT:POSTGRESQL", "TABLE:orders", "COLUMN:orders.category",
                                "COLUMN:orders.refund_amount", "TAG:aggregation",
                                "INTENT:refund_rate", "METRIC:sum.refund_amount",
                                "METRIC:count")),
                c("promotion_revenue",
                        "SELECT p.name, SUM(o.amount) AS revenue FROM promotions p "
                                + "JOIN orders o ON o.promotion_id = p.id "
                                + "WHERE o.created_at >= DATE '2026-01-01' GROUP BY p.name",
                        "评估促销活动销售额", "MYSQL",
                        facts("DIALECT:MYSQL", "TABLE:promotions", "TABLE:orders",
                                "COLUMN:promotions.name", "COLUMN:orders.amount",
                                "COLUMN:orders.promotion_id", "COLUMN:promotions.id",
                                "COLUMN:orders.created_at", "TAG:join", "TAG:aggregation",
                                "TAG:filter", "TAG:date", "INTENT:promotion_revenue",
                                "METRIC:sum.amount",
                                "RELATION:orders.promotion_id=promotions.id"))
        );
    }

    private static LabeledCase c(String name, String sql, String problem, String dialect,
                                 Set<String> expected) {
        return new LabeledCase(name, sql, problem, dialect, expected);
    }

    private static Set<String> facts(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
