package com.dataagent.bootstrap;

import com.dataagent.service.LineageMaintenanceService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class LineageInitializer implements ApplicationRunner {

    private final LineageMaintenanceService lineage;

    public LineageInitializer(LineageMaintenanceService lineage) {
        this.lineage = lineage;
    }

    @Override
    public void run(ApplicationArguments args) {
        lineage.rebuildAll();
    }
}
