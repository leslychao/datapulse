package io.datapulse.bidding.domain.guard;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;

/**
 * Blocks BID_UP when the product's ad spend today exceeds a configured
 * daily budget. The limit is read from policyConfig's "maxDailySpend"
 * field (BigDecimal, in roubles). If not configured, the guard passes.
 */
@Component
public class DailySpendLimitGuard implements BiddingGuard {

  @Override
  public String guardName() {
    return "daily_spend_limit_guard";
  }

  @Override
  public int order() {
    return 65;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    if (context.proposedDecision() != BidDecisionType.BID_UP) {
      return BiddingGuardResult.allow(guardName());
    }

    BigDecimal maxDailySpend = readMaxDailySpend(context);
    if (maxDailySpend == null) {
      return BiddingGuardResult.allow(guardName());
    }

    BigDecimal adSpend = context.signals().adSpend();
    if (adSpend == null) {
      return BiddingGuardResult.allow(guardName());
    }

    if (adSpend.compareTo(maxDailySpend) >= 0) {
      return BiddingGuardResult.block(guardName(),
          MessageCodes.BIDDING_GUARD_DAILY_SPEND_LIMIT,
          Map.of(
              "spent", adSpend.toPlainString(),
              "limit", maxDailySpend.toPlainString()));
    }

    return BiddingGuardResult.allow(guardName());
  }

  private BigDecimal readMaxDailySpend(BiddingGuardContext context) {
    if (context.policyConfig() != null
        && context.policyConfig().has("maxDailySpend")
        && !context.policyConfig().get("maxDailySpend").isNull()) {
      return context.policyConfig().get("maxDailySpend").decimalValue();
    }
    return null;
  }
}
