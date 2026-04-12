package io.datapulse.bidding.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.bidding.persistence.BidActionRepository;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import io.datapulse.bidding.persistence.BiddingDataReadRepository;
import io.datapulse.bidding.persistence.CampaignInfoRow;
import io.datapulse.bidding.persistence.EligibleProductRow;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiddingActionScheduler {

  private static final List<BidDecisionType> ACTIONABLE_TYPES = List.of(
      BidDecisionType.BID_UP,
      BidDecisionType.BID_DOWN,
      BidDecisionType.PAUSE,
      BidDecisionType.RESUME,
      BidDecisionType.SET_MINIMUM,
      BidDecisionType.EMERGENCY_CUT);

  private static final List<BidActionStatus> SUPERSEDABLE_STATUSES = List.of(
      BidActionStatus.PENDING_APPROVAL,
      BidActionStatus.APPROVED,
      BidActionStatus.SCHEDULED,
      BidActionStatus.ON_HOLD);

  private final BidDecisionRepository decisionRepository;
  private final BidActionRepository actionRepository;
  private final BiddingDataReadRepository readRepository;
  private final OutboxService outboxService;

  @Transactional
  public void scheduleActions(long biddingRunId) {
    List<BidDecisionEntity> decisions = decisionRepository.findByBiddingRunId(biddingRunId);

    List<BidDecisionEntity> actionable = decisions.stream()
        .filter(d -> ACTIONABLE_TYPES.contains(d.getDecisionType()))
        .toList();

    if (actionable.isEmpty()) {
      log.debug("No actionable decisions for bidding run: runId={}", biddingRunId);
      return;
    }

    int created = 0;
    int superseded = 0;

    for (BidDecisionEntity decision : actionable) {
      superseded += supersedeExistingActions(decision.getMarketplaceOfferId());

      BidActionEntity action = createAction(decision);
      actionRepository.save(action);

      if (action.getStatus() == BidActionStatus.APPROVED) {
        outboxService.createEvent(
            OutboxEventType.BID_ACTION_EXECUTE,
            "bid_action",
            action.getId(),
            Map.of("bidActionId", action.getId()));
      }

      created++;
    }

    log.info("Bid actions scheduled: runId={}, created={}, superseded={}",
        biddingRunId, created, superseded);
  }

  private int supersedeExistingActions(long marketplaceOfferId) {
    List<BidActionEntity> existing = actionRepository
        .findByMarketplaceOfferIdAndStatusIn(marketplaceOfferId, SUPERSEDABLE_STATUSES);

    for (BidActionEntity old : existing) {
      old.setStatus(BidActionStatus.SUPERSEDED);
    }

    if (!existing.isEmpty()) {
      actionRepository.saveAll(existing);
    }

    return existing.size();
  }

  private BidActionEntity createAction(BidDecisionEntity decision) {
    ExecutionMode mode = ExecutionMode.valueOf(decision.getExecutionMode());

    var action = new BidActionEntity();
    action.setBidDecisionId(decision.getId());
    action.setWorkspaceId(decision.getWorkspaceId());
    action.setMarketplaceOfferId(decision.getMarketplaceOfferId());
    action.setTargetBid(resolveTargetBid(decision));
    action.setPreviousBid(decision.getCurrentBid());
    action.setExecutionMode(decision.getExecutionMode());
    action.setMaxRetries(3);

    CampaignInfoRow campaign = readRepository
        .findCampaignInfo(decision.getMarketplaceOfferId())
        .orElse(null);

    if (campaign != null) {
      action.setCampaignExternalId(campaign.campaignExternalId());
      action.setMarketplaceType(campaign.marketplaceType());
    } else {
      action.setCampaignExternalId("UNKNOWN");
      action.setMarketplaceType("UNKNOWN");
      log.warn("No campaign info found for offer: marketplaceOfferId={}, "
              + "decisionId={}, setting defaults",
          decision.getMarketplaceOfferId(), decision.getId());
    }

    EligibleProductRow product = resolveProduct(decision);
    action.setConnectionId(product.connectionId());
    action.setNmId(product.marketplaceSku());

    action.setStatus(resolveInitialStatus(mode));
    if (action.getStatus() == BidActionStatus.APPROVED) {
      action.setApprovedAt(OffsetDateTime.now());
    }

    return action;
  }

  private int resolveTargetBid(BidDecisionEntity decision) {
    if (decision.getDecisionType() == BidDecisionType.PAUSE) {
      return 0;
    }
    if (decision.getTargetBid() == null) {
      throw new IllegalStateException(
          "targetBid is null for actionable decision: decisionId=%d, type=%s"
              .formatted(decision.getId(), decision.getDecisionType()));
    }
    return decision.getTargetBid();
  }

  private EligibleProductRow resolveProduct(BidDecisionEntity decision) {
    List<EligibleProductRow> products = readRepository.findEligibleProducts(
        decision.getWorkspaceId(), decision.getBidPolicyId());

    return products.stream()
        .filter(p -> p.marketplaceOfferId() == decision.getMarketplaceOfferId())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Cannot resolve product for offer: marketplaceOfferId=%d"
                .formatted(decision.getMarketplaceOfferId())));
  }

  private BidActionStatus resolveInitialStatus(ExecutionMode mode) {
    return switch (mode) {
      case RECOMMENDATION -> BidActionStatus.ON_HOLD;
      case SEMI_AUTO -> BidActionStatus.PENDING_APPROVAL;
      case FULL_AUTO -> BidActionStatus.APPROVED;
    };
  }
}
