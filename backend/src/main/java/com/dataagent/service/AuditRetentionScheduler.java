package com.dataagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionScheduler.class);

    private final AuditExportService export;

    public AuditRetentionScheduler(AuditExportService export) {
        this.export = export;
    }

    @Scheduled(cron = "${dataagent.audit.retention-cron:0 30 4 * * *}")
    public void purge() {
        try {
            export.purgeExpired();
        } catch (Exception error) {
            log.warn("Audit retention purge failed: {}", error.getMessage());
        }
    }
}
