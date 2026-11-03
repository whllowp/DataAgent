package com.dataagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryScheduler.class);

    private final MemoryService memories;

    public MemoryScheduler(MemoryService memories) {
        this.memories = memories;
    }

    @Scheduled(cron = "${dataagent.memory.archive-cron:0 15 3 * * *}")
    public void archiveExpired() {
        try {
            int archived = memories.archiveExpired().archived();
            if (archived > 0) {
                log.info("Archived {} expired long-term memories", archived);
            }
        } catch (Exception e) {
            log.warn("Long-term memory archive failed: {}", e.getMessage());
        }
    }
}
