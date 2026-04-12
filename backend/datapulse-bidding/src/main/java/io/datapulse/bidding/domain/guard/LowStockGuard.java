package io.datapulse.bidding.domain.guard;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;

/**
 * Blocks BID_UP when stock coverage is dangerously low (but not zero —
 * zero stock is handled by {@link BidStockOutGuard}).
 */
@Component
@RequiredArgsConstructor
public class LowStockGuard implements BiddingGuard {

  private final BiddingProperties biddingProperties;

  @Override
  public String guardName() {
    return "low_stock_guard";
  }

  @Override
  public int order() {
    return 45;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    Integer stockDays = context.signals().stockDays();
    int threshold = biddingProperties.getLowStockThresholdDays();

    if (stockDays != null
        && stockDays > 0
        && stockDays < threshold
        && context.proposedDecision() == BidDecisionType.BID_UP) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_LOW_STOCK,
          Map.of("days", stockDays, "threshold", threshold));
    }
    return BiddingGuardResult.allow(guardName());
  }
}
