package io.datapulse.bidding.domain;

import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pre-strategy check: if the last decision for an offer was PAUSE,
 * and the pause condition has resolved, generate a RESUME decision
 * so the offer re-enters the normal strategy evaluation on the next run.
 */
@Service
@RequiredArgsConstructor
public class BiddingResumeEvaluator {

  private final BidDecisionRepository decisionRepository;

  /**
   * @return RESUME result if conditions are met, null otherwise
   */
  public BiddingStrategyResult evaluateResume(
      long workspaceId,
      long marketplaceOfferId,
      BiddingSignalSet signals) {

    var lastDecision = decisionRepository
        .findFirstByWorkspaceIdAndMarketplaceOfferIdOrderByCreatedAtDesc(
            workspaceId, marketplaceOfferId);

    if (lastDecision.isEmpty()) {
      return null;
    }

    BidDecisionEntity last = lastDecision.get();
    if (last.getDecisionType() != BidDecisionType.PAUSE) {
      return null;
    }

    if (isPauseReasonResolved(last, signals)) {
      return new BiddingStrategyResult(
          BidDecisionType.RESUME,
          last.getCurrentBid(),
          "Resume: previous PAUSE reason resolved. Restoring bid to %s"
              .formatted(last.getCurrentBid()));
    }

    return null;
  }

  private boolean isPauseReasonResolved(
      BidDecisionEntity pauseDecision, BiddingSignalSet signals) {
    String explanation = pauseDecision.getExplanationSummary();
    if (explanation == null) {
      return false;
    }

    if (explanation.contains("stock") || explanation.contains("Stock")) {
      return signals.availableStock() != null && signals.availableStock() > 0;
    }
    if (explanation.contains("margin") || explanation.contains("Margin")) {
      return signals.marginPct() != null
          && signals.marginPct().signum() > 0;
    }

    return false;
  }
}
