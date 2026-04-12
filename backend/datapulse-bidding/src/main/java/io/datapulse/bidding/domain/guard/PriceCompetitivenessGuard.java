package io.datapulse.bidding.domain.guard;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;

/**
 * Blocks BID_UP when the current bid is already significantly above
 * the competitive bid (indicating the seller already pays more than average).
 *
 * Threshold is read from policyConfig's "competitivenessPremiumPct"
 * (default: 50 — meaning current bid is 50%+ above competitive bid).
 * Disabled if no competitive bid data is available.
 */
@Component
public class PriceCompetitivenessGuard implements BiddingGuard {

  private static final int DEFAULT_PREMIUM_THRESHOLD_PCT = 50;

  @Override
  public String guardName() {
    return "price_competitiveness_guard";
  }

  @Override
  public int order() {
    return 75;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    if (context.proposedDecision() != BidDecisionType.BID_UP) {
      return BiddingGuardResult.allow(guardName());
    }

    Integer currentBid = context.currentBid();
    Integer competitiveBid = context.signals().competitiveBid();

    if (currentBid == null || competitiveBid == null
        || competitiveBid <= 0) {
      return BiddingGuardResult.allow(guardName());
    }

    int premiumThreshold = readPremiumThreshold(context);
    int premiumPct =
        (int) ((currentBid - competitiveBid) * 100L / competitiveBid);

    if (premiumPct >= premiumThreshold) {
      return BiddingGuardResult.block(guardName(),
          MessageCodes.BIDDING_GUARD_PRICE_COMPETITIVENESS,
          Map.of(
              "currentBid", currentBid,
              "competitiveBid", competitiveBid,
              "premiumPct", premiumPct,
              "threshold", premiumThreshold));
    }

    return BiddingGuardResult.allow(guardName());
  }

  private int readPremiumThreshold(BiddingGuardContext context) {
    if (context.policyConfig() != null
        && context.policyConfig().has("competitivenessPremiumPct")
        && !context.policyConfig().get("competitivenessPremiumPct").isNull()) {
      return context.policyConfig().get("competitivenessPremiumPct").intValue();
    }
    return DEFAULT_PREMIUM_THRESHOLD_PCT;
  }
}
