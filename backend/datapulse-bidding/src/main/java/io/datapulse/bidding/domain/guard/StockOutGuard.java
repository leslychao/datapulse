package io.datapulse.bidding.domain.guard;

import java.util.Set;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;

/**
 * Blocks BID_UP and SET_MINIMUM when stock is depleted (stockDays == 0).
 * HOLD and BID_DOWN remain allowed — seller may still want to lower the bid.
 */
@Component
public class StockOutGuard implements BiddingGuard {

  private static final Set<BidDecisionType> BLOCKED_DECISIONS =
      Set.of(BidDecisionType.BID_UP, BidDecisionType.SET_MINIMUM);

  @Override
  public String guardName() {
    return "stock_out_guard";
  }

  @Override
  public int order() {
    return 40;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    Integer stockDays = context.signals().stockDays();

    if (stockDays != null && stockDays == 0
        && BLOCKED_DECISIONS.contains(context.proposedDecision())) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_STOCK_OUT);
    }
    return BiddingGuardResult.allow(guardName());
  }
}
