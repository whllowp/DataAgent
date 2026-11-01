package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SemanticSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SemanticSyncScheduler.class);

    private final SemanticSyncService syncService;
    private final DataAgentProperties properties;

    public SemanticSyncScheduler(SemanticSyncService syncService, DataAgentProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${dataagent.semantic.sync-interval-ms:1800000}",
            initialDelayString = "${dataagent.semantic.sync-initial-delay-ms:120000}")
    public void sync() {
        if (!properties.getSemantic().isSyncEnabled()) {
            return;
        }
        try {
            syncService.sync("SCHEDULED");
        } catch (Exception e) {
            log.error("Scheduled semantic sync failed", e);
        }
    }
}
