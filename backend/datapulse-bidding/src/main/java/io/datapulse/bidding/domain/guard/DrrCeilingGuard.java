package io.datapulse.bidding.domain.guard;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;

/**
 * Blocks BID_UP when the current DRR exceeds the configured ceiling.
 * The ceiling is read from policyConfig's {@code drrCeiling} field
 * (default: 30%).
 */
@Component
public class DrrCeilingGuard implements BiddingGuard {

  private static final BigDecimal DEFAULT_DRR_CEILING = BigDecimal.valueOf(30);

  @Override
  public String guardName() {
    return "drr_ceiling_guard";
  }

  @Override
  public int order() {
    return 60;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    BigDecimal drrPct = context.signals().drrPct();
    if (drrPct == null || context.proposedDecision() != BidDecisionType.BID_UP) {
      return BiddingGuardResult.allow(guardName());
    }

    BigDecimal drrCeiling = readDrrCeiling(context);

    if (drrPct.compareTo(drrCeiling) > 0) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_DRR_CEILING,
          Map.of(
              "drr", drrPct.toPlainString(),
              "ceiling", drrCeiling.toPlainString()));
    }
    return BiddingGuardResult.allow(guardName());
  }

  private BigDecimal readDrrCeiling(BiddingGuardContext context) {
    if (context.policyConfig() != null
        && context.policyConfig().has("drrCeiling")
        && !context.policyConfig().get("drrCeiling").isNull()) {
      return context.policyConfig().get("drrCeiling").decimalValue();
    }
    return DEFAULT_DRR_CEILING;
  }
}
