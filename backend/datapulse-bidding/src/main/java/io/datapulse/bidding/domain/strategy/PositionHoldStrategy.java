package io.datapulse.bidding.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.bidding.domain.BiddingStrategyResult;
import io.datapulse.bidding.domain.BiddingStrategyType;

/**
 * POSITION_HOLD — keep impressions within a target band while respecting
 * a DRR ceiling.
 *
 * Logic:
 *   - Impressions below target band AND DRR within ceiling → BID_UP
 *   - Impressions above target band → BID_DOWN (save budget)
 *   - DRR exceeds ceiling → BID_DOWN regardless of impressions
 *   - Within tolerance → HOLD
 */
@Component
public class PositionHoldStrategy implements BiddingStrategy {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int DEFAULT_MIN_BID = 50;
  private static final BigDecimal DEFAULT_TOLERANCE_PCT = BigDecimal.valueOf(20);
  private static final BigDecimal DEFAULT_STEP_PCT = BigDecimal.TEN;

  @Override
  public BiddingStrategyType strategyType() {
    return BiddingStrategyType.POSITION_HOLD;
  }

  @Override
  public BiddingStrategyResult evaluate(
      BiddingSignalSet signals, JsonNode policyConfig) {

    if (signals.currentBid() == null) {
      return BiddingStrategyResult.hold("currentBid is null — no data");
    }

    Long targetImpressions = longField(policyConfig,
        "targetImpressionsDaily");
    if (targetImpressions == null) {
      return BiddingStrategyResult.hold(
          "targetImpressionsDaily not configured");
    }

    BigDecimal tolerancePct = decimalField(policyConfig,
        "impressionsTolerancePct", DEFAULT_TOLERANCE_PCT);
    BigDecimal ceilingDrr = decimalField(policyConfig,
        "ceilingDrrPct", null);
    BigDecimal stepPct = decimalField(policyConfig, "bidStepPct",
        DEFAULT_STEP_PCT);
    Integer lookbackDays = intFieldNullable(policyConfig, "lookbackDays");

    int currentBid = signals.currentBid();
    long impressions = signals.impressions();

    BigDecimal tolFraction = tolerancePct
        .divide(HUNDRED, 4, RoundingMode.HALF_UP);
    long upperBound = Math.round(
        targetImpressions * (1 + tolFraction.doubleValue()));
    long lowerBound = Math.round(
        targetImpressions * (1 - tolFraction.doubleValue()));

    if (ceilingDrr != null && signals.drrPct() != null
        && signals.drrPct().compareTo(ceilingDrr) > 0) {
      int target = applyStep(currentBid, stepPct, false, null,
          signals.minBid());
      return new BiddingStrategyResult(BidDecisionType.BID_DOWN, target,
          explanation(impressions, targetImpressions, tolerancePct,
              "BID_DOWN (DRR ceiling breached: %s%% > %s%%)"
                  .formatted(
                      signals.drrPct().setScale(1, RoundingMode.HALF_UP),
                      ceilingDrr.setScale(1, RoundingMode.HALF_UP)),
              currentBid, target));
    }

    if (impressions < lowerBound) {
      int target = applyStep(currentBid, stepPct, true, null,
          signals.minBid());
      return new BiddingStrategyResult(BidDecisionType.BID_UP, target,
          explanation(impressions, targetImpressions, tolerancePct,
              "BID_UP", currentBid, target));
    }

    if (impressions > upperBound) {
      int target = applyStep(currentBid, stepPct, false, null,
          signals.minBid());
      return new BiddingStrategyResult(BidDecisionType.BID_DOWN, target,
          explanation(impressions, targetImpressions, tolerancePct,
              "BID_DOWN", currentBid, target));
    }

    return new BiddingStrategyResult(BidDecisionType.HOLD, currentBid,
        explanation(impressions, targetImpressions, tolerancePct,
            "HOLD", currentBid, currentBid));
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

  private String explanation(long impressions, long target,
      BigDecimal tolerancePct, String decision,
      int currentBid, int targetBid) {
    return "Position hold: impressions %d vs target %d ±%s%%. "
        .formatted(impressions, target,
            tolerancePct.setScale(0, RoundingMode.HALF_UP))
        + "Decision: %s. Bid: %d → %d"
            .formatted(decision, currentBid, targetBid);
  }

  private BigDecimal decimalField(JsonNode cfg, String field,
      BigDecimal def) {
    if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) {
      return def;
    }
    return cfg.get(field).decimalValue();
  }

  private Long longField(JsonNode cfg, String field) {
    if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) {
      return null;
    }
    return cfg.get(field).longValue();
  }

  private Integer intFieldNullable(JsonNode cfg, String field) {
    if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) {
      return null;
    }
    return cfg.get(field).intValue();
  }
}
