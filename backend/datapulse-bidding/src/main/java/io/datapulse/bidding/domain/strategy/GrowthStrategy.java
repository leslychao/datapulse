package io.datapulse.bidding.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.bidding.domain.BiddingStrategyResult;
import io.datapulse.bidding.domain.BiddingStrategyType;

import java.util.Map;

/**
 * GROWTH — maximize ad orders within an acceptable CPO ceiling.
 *
 * Logic:
 *   - CPO below target AND headroom exists → BID_UP
 *   - CPO exceeds max allowed → BID_DOWN
 *   - Not enough clicks for a reliable signal → HOLD
 */
@Component
public class GrowthStrategy implements BiddingStrategy {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int DEFAULT_MIN_BID = 50;
  private static final int DEFAULT_MIN_CLICKS = 10;
  private static final BigDecimal DEFAULT_STEP_PCT = BigDecimal.TEN;

  @Override
  public BiddingStrategyType strategyType() {
    return BiddingStrategyType.GROWTH;
  }

  @Override
  public BiddingStrategyResult evaluate(
      BiddingSignalSet signals, JsonNode policyConfig) {

    if (signals.currentBid() == null) {
      return BiddingStrategyResult.hold("currentBid is null — no data");
    }

    BigDecimal targetCpo = decimalField(policyConfig, "targetCpo", null);
    BigDecimal maxCpo = decimalField(policyConfig, "maxCpo", null);
    if (targetCpo == null || maxCpo == null) {
      return BiddingStrategyResult.hold("targetCpo or maxCpo not configured");
    }

    int minClicks = intField(policyConfig, "minClicksForSignal",
        DEFAULT_MIN_CLICKS);
    if (signals.clicks() < minClicks) {
      return BiddingStrategyResult.hold(
          "Insufficient clicks: %d < %d minimum"
              .formatted(signals.clicks(), minClicks));
    }

    BigDecimal cpoPct = signals.cpoPct();
    if (cpoPct == null) {
      return BiddingStrategyResult.hold("cpoPct is null — no data");
    }

    BigDecimal stepPct = decimalField(policyConfig, "bidStepPct",
        DEFAULT_STEP_PCT);
    Integer maxBid = intFieldNullable(policyConfig, "maxBid");
    int currentBid = signals.currentBid();

    if (cpoPct.compareTo(maxCpo) > 0) {
      int target = applyStep(currentBid, stepPct, false, null,
          signals.minBid());
      return BiddingStrategyResult.withMessage(
          BidDecisionType.BID_DOWN, target,
          explanation(cpoPct, targetCpo, maxCpo, "BID_DOWN", currentBid, target),
          "bidding.strategy.growth.bid_down",
          growthArgs(cpoPct, targetCpo, maxCpo, currentBid, target));
    }

    if (cpoPct.compareTo(targetCpo) < 0 && hasGrowthHeadroom(signals)) {
      int target = applyStep(currentBid, stepPct, true, maxBid,
          signals.minBid());
      return BiddingStrategyResult.withMessage(
          BidDecisionType.BID_UP, target,
          explanation(cpoPct, targetCpo, maxCpo, "BID_UP", currentBid, target),
          "bidding.strategy.growth.bid_up",
          growthArgs(cpoPct, targetCpo, maxCpo, currentBid, target));
    }

    return BiddingStrategyResult.withMessage(
        BidDecisionType.HOLD, currentBid,
        explanation(cpoPct, targetCpo, maxCpo, "HOLD", currentBid, currentBid),
        "bidding.strategy.growth.hold",
        growthArgs(cpoPct, targetCpo, maxCpo, currentBid, currentBid));
  }

  private boolean hasGrowthHeadroom(BiddingSignalSet signals) {
    if (signals.roas() != null
        && signals.roas().compareTo(BigDecimal.ONE) <= 0) {
      return false;
    }
    return true;
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

  private String explanation(BigDecimal cpoPct, BigDecimal targetCpo,
      BigDecimal maxCpo, String decision, int currentBid, int targetBid) {
    return "Growth: CPO %s vs target %s / max %s. Decision: %s. Bid: %d → %d"
        .formatted(
            cpoPct.setScale(1, RoundingMode.HALF_UP).toPlainString(),
            targetCpo.setScale(1, RoundingMode.HALF_UP).toPlainString(),
            maxCpo.setScale(1, RoundingMode.HALF_UP).toPlainString(),
            decision, currentBid, targetBid);
  }

  private Map<String, Object> growthArgs(
      BigDecimal cpoPct, BigDecimal targetCpo, BigDecimal maxCpo,
      int currentBid, int targetBid) {
    return Map.of(
        "cpoPct", cpoPct.setScale(1, RoundingMode.HALF_UP).toPlainString(),
        "targetCpo", targetCpo.setScale(1, RoundingMode.HALF_UP).toPlainString(),
        "maxCpo", maxCpo.setScale(1, RoundingMode.HALF_UP).toPlainString(),
        "currentBid", currentBid,
        "targetBid", targetBid);
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

  private Integer intFieldNullable(JsonNode cfg, String field) {
    if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) {
      return null;
    }
    return cfg.get(field).intValue();
  }
}
