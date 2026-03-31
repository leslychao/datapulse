package io.datapulse.etl.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final RetentionService retentionService;

    @Scheduled(cron = "${datapulse.etl.retention.cron:0 0 3 * * ?}")
    @SchedulerLock(name = "rawRetention", lockAtMostFor = "PT1H")
    public void runRetention() {
        try {
            log.info("Raw layer retention started");
            retentionService.runRetention();
            log.info("Raw layer retention completed");
        } catch (Exception e) {
            log.error("Raw layer retention failed", e);
        }
    }
}
