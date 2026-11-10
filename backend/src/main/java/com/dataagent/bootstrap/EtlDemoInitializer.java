package com.dataagent.bootstrap;

import com.dataagent.service.EtlService;
import com.dataagent.web.dto.DolphinSchedulerDtos;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(30)
public class EtlDemoInitializer implements ApplicationRunner {

    private final EtlService etl;

    public EtlDemoInitializer(EtlService etl) {
        this.etl = etl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!etl.list().isEmpty()) {
            return;
        }
        etl.importDolphinScheduler(new DolphinSchedulerDtos.ImportRequest(
                "default", 10001L, "demo-sales", List.of(
                new DolphinSchedulerDtos.ProcessDefinition(1001L, "ods_to_dwd_orders",
                        "清洗订单并补充客户城市", "ONLINE",
                        new DolphinSchedulerDtos.Schedule("0 0 2 * * ?", null,
                                "Asia/Shanghai", null, "ONLINE"),
                        List.of(
                                task(101L, "extract_orders", "SQL",
                                        "INSERT OVERWRITE TABLE dwd_orders "
                                                + "SELECT o.id, o.customer_id, o.amount, o.order_date "
                                                + "FROM ods_orders o WHERE o.status = 'PAID'"),
                                task(102L, "enrich_customer", "SQL",
                                        "INSERT OVERWRITE TABLE dwd_order_customer "
                                                + "SELECT o.*, c.city FROM dwd_orders o "
                                                + "JOIN ods_customers c ON c.id = o.customer_id")
                        ),
                        null,
                        List.of(
                                new DolphinSchedulerDtos.TaskRelation(101L, 102L, "ALL_SUCCESS", 0L)
                        ),
                        null, List.of()),
                new DolphinSchedulerDtos.ProcessDefinition(1002L, "dwd_to_ads_city",
                        "按城市汇总 GMV", "ONLINE",
                        new DolphinSchedulerDtos.Schedule("0 30 2 * * ?", null,
                                "Asia/Shanghai", null, "ONLINE"),
                        List.of(
                                task(201L, "aggregate_city", "SQL",
                                        "INSERT OVERWRITE TABLE ads_city_gmv "
                                                + "SELECT city, SUM(amount) AS gmv "
                                                + "FROM dwd_order_customer GROUP BY city")
                        ),
                        null,
                        List.of(),
                        null,
                        List.of(new DolphinSchedulerDtos.WorkflowDependency(
                                1001L, null, 1002L, null, "ALL_SUCCESS", 0L)))
        )));
    }

    private static DolphinSchedulerDtos.TaskDefinition task(Long code, String name, String type, String sql) {
        return new DolphinSchedulerDtos.TaskDefinition(code, name, type, type,
                Map.of("sql", sql), null, null, null);
    }
}
