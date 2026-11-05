package com.devflow.devflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

@Component
public class SprintBurndownSnapshotScheduler {
    private static final Logger logger = LoggerFactory.getLogger(SprintBurndownSnapshotScheduler.class);
    private final SprintBurndownSnapshotService snapshotService;

    public SprintBurndownSnapshotScheduler(SprintBurndownSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void captureAfterStartup() {
        collectSafely("startup");
    }

    @Scheduled(cron = "${devflow.analytics.burndown-cron:0 5 0 * * *}", zone = "UTC")
    public void captureDaily() {
        collectSafely("daily schedule");
    }

    private void collectSafely(String trigger) {
        try {
            int collected = snapshotService.collectTodaySnapshots();
            logger.info("Sprint burndown snapshot collection completed: trigger={}, collected={}", trigger, collected);
        } catch (RuntimeException exception) {
            logger.error("Sprint burndown snapshot collection failed: trigger={}", trigger, exception);
        }
    }
}
