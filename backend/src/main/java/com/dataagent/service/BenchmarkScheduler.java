package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BenchmarkScheduler {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkScheduler.class);

    private final BenchmarkService benchmarks;
    private final DataAgentProperties properties;

    public BenchmarkScheduler(BenchmarkService benchmarks, DataAgentProperties properties) {
        this.benchmarks = benchmarks;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${dataagent.benchmark.sync-interval-ms:3600000}",
            initialDelayString = "${dataagent.benchmark.sync-initial-delay-ms:300000}")
    public void runScheduled() {
        if (!properties.getBenchmark().isSyncEnabled()) {
            return;
        }
        try {
            var result = benchmarks.runAll();
            log.info("Scheduled benchmark sync: attempted={}, succeeded={}, failed={}",
                    result.attempted(), result.succeeded(), result.failed());
        } catch (Exception e) {
            log.warn("Scheduled benchmark sync failed: {}", e.getMessage());
        }
    }
}
