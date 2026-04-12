package io.datapulse.bidding.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.bidding.domain.BiddingStrategyResult;
import io.datapulse.bidding.domain.BiddingStrategyType;
import io.datapulse.bidding.domain.PauseReasonCode;

/**
 * LIQUIDATION — accelerate sales for overstock / end-of-season products.
 * Allows higher DRR than ECONOMY_HOLD.
 *
 * Logic:
 *   - Stock approaching exit threshold → slow down (BID_DOWN / HOLD)
 *   - DRR within allowed range AND velocity can grow → BID_UP
 *   - DRR exceeds max → BID_DOWN
 *   - Stock depleted → PAUSE
 */
@Component
public class LiquidationStrategy implements BiddingStrategy {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int DEFAULT_MIN_BID = 50;
  private static final BigDecimal DEFAULT_MAX_DRR = BigDecimal.valueOf(25);
  private static final BigDecimal DEFAULT_STEP_PCT = BigDecimal.valueOf(15);
  private static final int DEFAULT_EXIT_DAYS_OF_COVER = 7;

  @Override
  public BiddingStrategyType strategyType() {
    return BiddingStrategyType.LIQUIDATION;
  }

  @Override
  public BiddingStrategyResult evaluate(
      BiddingSignalSet signals, JsonNode policyConfig) {

    if (signals.currentBid() == null) {
      return BiddingStrategyResult.hold("currentBid is null — no data");
    }

    int currentBid = signals.currentBid();
    BigDecimal maxDrr = decimalField(policyConfig, "maxDrrPct",
        DEFAULT_MAX_DRR);
    BigDecimal stepPct = decimalField(policyConfig, "bidStepPct",
        DEFAULT_STEP_PCT);
    int exitDaysOfCover = intField(policyConfig, "exitDaysOfCover",
        DEFAULT_EXIT_DAYS_OF_COVER);

    if (signals.stockDays() != null && signals.stockDays() == 0) {
      return BiddingStrategyResult.pause(PauseReasonCode.STOCK_OUT,
          "Liquidation: stock depleted (stockDays=0). PAUSE");
    }

    if (signals.stockDays() != null
        && signals.stockDays() <= exitDaysOfCover) {
      int target = applyStep(currentBid, stepPct, false, null,
          signals.minBid());
      return new BiddingStrategyResult(BidDecisionType.BID_DOWN, target,
          "Liquidation: stockDays %d <= exit threshold %d. "
              .formatted(signals.stockDays(), exitDaysOfCover)
              + "Winding down: %d → %d"
                  .formatted(currentBid, target));
    }

    if (signals.drrPct() != null
        && signals.drrPct().compareTo(maxDrr) > 0) {
      int target = applyStep(currentBid, stepPct, false, null,
          signals.minBid());
      return new BiddingStrategyResult(BidDecisionType.BID_DOWN, target,
          "Liquidation: DRR %s%% > max %s%%. BID_DOWN: %d → %d"
              .formatted(
                  signals.drrPct().setScale(1, RoundingMode.HALF_UP),
                  maxDrr.setScale(1, RoundingMode.HALF_UP),
                  currentBid, target));
    }

    if (signals.drrPct() == null
        || signals.drrPct().compareTo(maxDrr) < 0) {
      int target = applyStep(currentBid, stepPct, true, null,
          signals.minBid());
      return new BiddingStrategyResult(BidDecisionType.BID_UP, target,
          "Liquidation: DRR %s within budget (max %s%%). BID_UP: %d → %d"
              .formatted(
                  signals.drrPct() != null
                      ? signals.drrPct().setScale(1, RoundingMode.HALF_UP)
                          .toPlainString() + "%"
                      : "n/a",
                  maxDrr.setScale(1, RoundingMode.HALF_UP),
                  currentBid, target));
    }

    return new BiddingStrategyResult(BidDecisionType.HOLD, currentBid,
        "Liquidation: DRR at max boundary. HOLD at %d"
            .formatted(currentBid));
  }

  private int applyStep(int currentBid, BigDecimal stepPct,
      boolean up, Integer maxBid, Integer minBid) {
    BigDecimal factor = up
        ? BigDecimal.ONE.add(
            stepPct.divide(HUNDRED, 4, RoundingMode.HALF_UP))
        : BigDecimal.ONE.subtract(
            stepPct.divide(HUNDRED, 4, RoundingMode.HALF_UP));
    int raw = Math.round(currentBid * factor.floatValue());
    if (up && maxBid != null && raw > maxBid) {
      raw = maxBid;
    }
    int floor = minBid != null
        ? Math.max(minBid, DEFAULT_MIN_BID) : DEFAULT_MIN_BID;
    return Math.max(raw, floor);
  }

  private BigDecimal decimalField(JsonNode cfg, String field,
      BigDecimal def) {
    if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) {
      return def;
    }
    return cfg.get(field).decimalValue();
  }

  private int intField(JsonNode cfg, String field, int def) {
    if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) {
      return def;
    }
    return cfg.get(field).intValue();
  }
}
