package io.datapulse.bidding.scheduling;

import io.datapulse.bidding.domain.BidPolicyStatus;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BiddingRunScheduler {

  private static final String BIDDING_RUN_AGGREGATE_TYPE = "bidding_run";

  private final BidPolicyRepository bidPolicyRepository;
  private final OutboxService outboxService;

  @Scheduled(cron = "${datapulse.bidding.run-cron:0 0 */6 * * *}")
  @SchedulerLock(name = "biddingRunScheduler", lockAtMostFor = "PT30M")
  public void triggerScheduledRuns() {
    try {
      List<BidPolicyEntity> activePolicies = bidPolicyRepository
          .findByStatus(BidPolicyStatus.ACTIVE);

      if (activePolicies.isEmpty()) {
        log.debug("No active bid policies found, skipping scheduled bidding run");
        return;
      }

      log.info("Triggering scheduled bidding runs for {} active policies",
          activePolicies.size());

      for (BidPolicyEntity policy : activePolicies) {
        try {
          outboxService.createEvent(
              OutboxEventType.BIDDING_RUN_EXECUTE,
              BIDDING_RUN_AGGREGATE_TYPE,
              policy.getId(),
              Map.of(
                  "workspaceId", policy.getWorkspaceId(),
                  "bidPolicyId", policy.getId()));
        } catch (Exception e) {
          log.warn("Failed to enqueue bidding run for policy={}: {}",
              policy.getId(), e.getMessage());
        }
      }
    } catch (Exception e) {
      log.error("Scheduled bidding run job failed", e);
    }
  }
}
