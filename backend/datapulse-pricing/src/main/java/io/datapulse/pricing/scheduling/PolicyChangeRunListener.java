package io.datapulse.pricing.scheduling;

import io.datapulse.pricing.domain.PolicyActivatedEvent;
import io.datapulse.pricing.domain.PolicyLogicChangedEvent;
import io.datapulse.pricing.domain.PricingRunApiService;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyChangeRunListener {

  private final PricePolicyAssignmentRepository assignmentRepository;
  private final PricingRunApiService pricingRunApiService;

  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onPolicyActivated(PolicyActivatedEvent event) {
    triggerRunsForPolicy(event.policyId(), event.workspaceId(), "activated");
  }

  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onPolicyLogicChanged(PolicyLogicChangedEvent event) {
    triggerRunsForPolicy(event.policyId(), event.workspaceId(), "logic_changed");
  }

  private void triggerRunsForPolicy(long policyId, long workspaceId, String reason) {
    List<Long> connectionIds = assignmentRepository.findDistinctConnectionIdsByPolicyId(policyId);

    if (connectionIds.isEmpty()) {
      log.debug("No connections for policy {}, skipping POLICY_CHANGE run", policyId);
      return;
    }

    log.info("Triggering POLICY_CHANGE runs: policyId={}, reason={}, connections={}",
        policyId, reason, connectionIds.size());

    for (Long connectionId : connectionIds) {
      try {
        pricingRunApiService.triggerPolicyChangeRun(connectionId, workspaceId);
      } catch (Exception e) {
        log.warn("Failed to trigger POLICY_CHANGE run for connectionId={}: {}",
            connectionId, e.getMessage());
      }
    }
  }
}
