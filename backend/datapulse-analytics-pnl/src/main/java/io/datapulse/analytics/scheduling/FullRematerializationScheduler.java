package io.datapulse.analytics.scheduling;

import io.datapulse.analytics.domain.MaterializationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FullRematerializationScheduler {

  private final MaterializationService materializationService;

  @Scheduled(cron = "${datapulse.materialization.daily-rematerialization-cron:0 0 2 * * *}")
  @SchedulerLock(name = "analyticsFullRematerialization",
      lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
  public void runDaily() {
    log.info("Daily full re-materialization triggered");
    try {
      materializationService.runFullRematerialization();
    } catch (Exception e) {
      log.error("Daily full re-materialization failed", e);
    }
  }
}
