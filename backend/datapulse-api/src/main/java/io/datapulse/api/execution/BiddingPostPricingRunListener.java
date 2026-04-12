package io.datapulse.api.execution;

import io.datapulse.bidding.domain.BidPolicyStatus;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import io.datapulse.pricing.domain.PricingRunCompletedEvent;
import io.datapulse.pricing.domain.RunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * After a pricing run completes successfully, triggers bidding runs
 * for all active bid policies in the same workspace.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiddingPostPricingRunListener {

  private static final String BIDDING_RUN_AGGREGATE_TYPE = "bidding_run";

  private final BidPolicyRepository bidPolicyRepository;
  private final OutboxService outboxService;

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPricingRunCompleted(PricingRunCompletedEvent event) {
    if (event.finalStatus() != RunStatus.COMPLETED) {
      return;
    }

    try {
      List<BidPolicyEntity> activePolicies = bidPolicyRepository
          .findByWorkspaceIdAndStatus(event.workspaceId(), BidPolicyStatus.ACTIVE);

      if (activePolicies.isEmpty()) {
        log.debug("No active bid policies for workspace={}, skipping bidding trigger",
            event.workspaceId());
        return;
      }

      log.info("Triggering bidding runs after pricing run: workspaceId={}, pricingRunId={}, "
              + "activePolicies={}",
          event.workspaceId(), event.pricingRunId(), activePolicies.size());

      for (BidPolicyEntity policy : activePolicies) {
        outboxService.createEvent(
            OutboxEventType.BIDDING_RUN_EXECUTE,
            BIDDING_RUN_AGGREGATE_TYPE,
            policy.getId(),
            Map.of(
                "workspaceId", event.workspaceId(),
                "bidPolicyId", policy.getId()));
      }
    } catch (Exception e) {
      log.error("Failed to trigger bidding runs after pricing run: pricingRunId={}, error={}",
          event.pricingRunId(), e.getMessage(), e);
    }
  }
}
