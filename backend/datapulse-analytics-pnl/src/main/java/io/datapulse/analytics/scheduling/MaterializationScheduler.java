package io.datapulse.analytics.scheduling;

import io.datapulse.analytics.domain.MaterializationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaterializationScheduler {

  private final MaterializationOrchestrator orchestrator;

  @Scheduled(cron = "${datapulse.materialization.daily-rematerialization-cron:0 0 2 * * *}")
  public void dailyFullRematerialization() {
    try {
      orchestrator.runFull();
    } catch (Exception e) {
      log.error("Daily full re-materialization failed", e);
    }
  }
}
