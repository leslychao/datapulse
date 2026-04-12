package io.datapulse.bidding.domain.guard;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;

/**
 * Blocks bid changes when the product has had too many direction reversals
 * within a recent period (e.g. BID_UP → BID_DOWN → BID_UP = 2 reversals).
 * This prevents "oscillation" where the system keeps flipping the bid.
 *
 * Configuration:
 *   volatilityMaxReversals (default 3)
 *   volatilityPeriodDays   (default 7)
 */
@Component
@RequiredArgsConstructor
public class VolatilityGuard implements BiddingGuard {

  private final BiddingProperties biddingProperties;
  private final BidDecisionRepository decisionRepository;

  @Override
  public String guardName() {
    return "volatility_guard";
  }

  @Override
  public int order() {
    return 70;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    if (context.proposedDecision() == BidDecisionType.HOLD) {
      return BiddingGuardResult.allow(guardName());
    }

    int maxReversals = biddingProperties.getVolatilityMaxReversals();
    int periodDays = biddingProperties.getVolatilityPeriodDays();

    int changes = decisionRepository.countDirectionChanges(
        context.marketplaceOfferId(), periodDays);

    if (changes >= maxReversals) {
      return BiddingGuardResult.block(guardName(),
          MessageCodes.BIDDING_GUARD_VOLATILITY,
          Map.of(
              "changes", changes,
              "maxChanges", maxReversals,
              "periodDays", periodDays));
    }

    return BiddingGuardResult.allow(guardName());
  }
}
