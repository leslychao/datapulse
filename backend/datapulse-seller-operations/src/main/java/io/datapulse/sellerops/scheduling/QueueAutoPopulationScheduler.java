package io.datapulse.sellerops.scheduling;

import io.datapulse.sellerops.domain.QueueAutoPopulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAutoPopulationScheduler {

    private final QueueAutoPopulationService autoPopulationService;

    @Scheduled(fixedDelayString = "${datapulse.queue.auto-populate-interval:PT5M}")
    @SchedulerLock(name = "queueAutoPopulation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void autoPopulateQueues() {
        try {
            autoPopulationService.populateAllQueues();
        } catch (Exception e) {
            log.error("Queue auto-population job failed: error={}", e.getMessage(), e);
        }
    }
}
