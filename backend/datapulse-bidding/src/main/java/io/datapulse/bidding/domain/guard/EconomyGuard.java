package io.datapulse.bidding.domain.guard;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;

/**
 * Blocks BID_UP when the product margin is non-positive —
 * increasing ad spend on a loss-making SKU is not advisable.
 */
@Component
public class EconomyGuard implements BiddingGuard {

  @Override
  public String guardName() {
    return "economy_guard";
  }

  @Override
  public int order() {
    return 50;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    BigDecimal marginPct = context.signals().marginPct();

    if (marginPct != null
        && marginPct.compareTo(BigDecimal.ZERO) <= 0
        && context.proposedDecision() == BidDecisionType.BID_UP) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_ECONOMY);
    }
    return BiddingGuardResult.allow(guardName());
  }
}
