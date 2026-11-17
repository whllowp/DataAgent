package com.dataagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeAccumulationScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAccumulationScheduler.class);

    private final KnowledgeAccumulationService accumulation;

    public KnowledgeAccumulationScheduler(KnowledgeAccumulationService accumulation) {
        this.accumulation = accumulation;
    }

    @Scheduled(cron = "${dataagent.export.accumulation-cron:0 15 3 * * *}")
    public void accumulateDaily() {
        try {
            var result = accumulation.accumulate("SCHEDULED", 3, "system");
            log.info("Scheduled knowledge accumulation: promoted {} patterns, total knowledge {}",
                    result.promoted(), result.totalKnowledge());
        } catch (Exception e) {
            log.warn("Scheduled knowledge accumulation failed: {}", e.getMessage());
        }
    }
}
