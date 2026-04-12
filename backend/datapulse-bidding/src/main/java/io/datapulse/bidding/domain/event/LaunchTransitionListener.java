package io.datapulse.bidding.domain.event;

import io.datapulse.bidding.domain.AssignmentScope;
import io.datapulse.bidding.domain.BidPolicyStatus;
import io.datapulse.bidding.persistence.BidPolicyAssignmentEntity;
import io.datapulse.bidding.persistence.BidPolicyAssignmentRepository;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LaunchTransitionListener {

  private final BidPolicyRepository policyRepository;
  private final BidPolicyAssignmentRepository assignmentRepository;

  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onLaunchTransitionRequested(LaunchTransitionRequestedEvent event) {
    var existing = assignmentRepository
        .findByMarketplaceOfferId(event.marketplaceOfferId());
    if (existing.isEmpty()) {
      log.warn("Launch transition: no assignment found for offerId={}",
          event.marketplaceOfferId());
      return;
    }

    var targetPolicy = policyRepository
        .findFirstByWorkspaceIdAndStrategyTypeAndStatus(
            event.workspaceId(), event.targetStrategy(), BidPolicyStatus.ACTIVE);

    if (targetPolicy.isEmpty()) {
      log.warn("Launch transition: no active {} policy in workspace={}. "
              + "Cannot auto-transition offerId={}",
          event.targetStrategy(), event.workspaceId(),
          event.marketplaceOfferId());
      return;
    }

    BidPolicyAssignmentEntity oldAssignment = existing.get();
    BidPolicyEntity newPolicy = targetPolicy.get();

    assignmentRepository.delete(oldAssignment);

    var newAssignment = new BidPolicyAssignmentEntity();
    newAssignment.setBidPolicyId(newPolicy.getId());
    newAssignment.setWorkspaceId(event.workspaceId());
    newAssignment.setMarketplaceOfferId(event.marketplaceOfferId());
    newAssignment.setAssignmentScope(AssignmentScope.OFFER);
    assignmentRepository.save(newAssignment);

    log.info("Launch transition: offerId={} moved from policy={} to policy={} ({})",
        event.marketplaceOfferId(), oldAssignment.getBidPolicyId(),
        newPolicy.getId(), event.targetStrategy());
  }
}
