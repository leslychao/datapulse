package io.datapulse.pricing.scheduling;

import io.datapulse.pricing.domain.PricingRunApiService;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PricingRunScheduler {

  private final PricePolicyAssignmentRepository assignmentRepository;
  private final PricingRunApiService pricingRunApiService;

  @Scheduled(cron = "${datapulse.pricing.scheduled-run-cron:0 0 6 * * *}")
  public void triggerScheduledRuns() {
    try {
      List<Object[]> connections = assignmentRepository.findDistinctConnectionsWithActivePolicies();

      if (connections.isEmpty()) {
        log.debug("No active policy assignments found, skipping scheduled run");
        return;
      }

      log.info("Triggering scheduled pricing runs for {} connections", connections.size());
      for (Object[] row : connections) {
        long connectionId = ((Number) row[0]).longValue();
        long workspaceId = ((Number) row[1]).longValue();
        try {
          pricingRunApiService.triggerScheduledRun(connectionId, workspaceId);
        } catch (Exception e) {
          log.warn("Failed to trigger scheduled run for connectionId={}: {}",
              connectionId, e.getMessage());
        }
      }
    } catch (Exception e) {
      log.error("Scheduled pricing run job failed", e);
    }
  }
}
