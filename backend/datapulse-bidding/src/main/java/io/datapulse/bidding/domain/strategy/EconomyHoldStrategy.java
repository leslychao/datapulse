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
 * ECONOMY_HOLD — keeps ad spend (DRR) within the target band.
 * Raises the bid only when DRR is below the lower bound AND ROAS
 * is above the configured minimum; lowers the bid when DRR exceeds
 * the upper bound.
 */
@Component
public class EconomyHoldStrategy implements BiddingStrategy {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int WB_MIN_BID_KOPECKS = 50;

  private static final BigDecimal DEFAULT_TOLERANCE_PCT = BigDecimal.TEN;
  private static final BigDecimal DEFAULT_STEP_UP_PCT = BigDecimal.TEN;
  private static final BigDecimal DEFAULT_STEP_DOWN_PCT = BigDecimal.valueOf(15);
  private static final BigDecimal DEFAULT_MIN_ROAS = BigDecimal.ONE;

  @Override
  public BiddingStrategyType strategyType() {
    return BiddingStrategyType.ECONOMY_HOLD;
  }

  @Override
  public BiddingStrategyResult evaluate(BiddingSignalSet signals, JsonNode policyConfig) {
    if (signals.currentBid() == null) {
      return hold(signals, "currentBid is null — no data to act on");
    }
    if (signals.drrPct() == null) {
      return hold(signals, "drrPct is null — no data to act on");
    }

    BigDecimal targetDrr = decimalField(policyConfig, "targetDrrPct", null);
    if (targetDrr == null) {
      return hold(signals, "targetDrrPct not configured");
    }

    BigDecimal tolerance = decimalField(policyConfig, "tolerancePct", DEFAULT_TOLERANCE_PCT);
    BigDecimal stepUp = decimalField(policyConfig, "stepUpPct", DEFAULT_STEP_UP_PCT);
    BigDecimal stepDown = decimalField(policyConfig, "stepDownPct", DEFAULT_STEP_DOWN_PCT);
    BigDecimal minRoas = decimalField(policyConfig, "minRoas", DEFAULT_MIN_ROAS);
    Integer maxBidKopecks = intField(policyConfig, "maxBidKopecks");

    BigDecimal toleranceFraction = tolerance.divide(HUNDRED, 4, RoundingMode.HALF_UP);
    BigDecimal upperBound = targetDrr.multiply(BigDecimal.ONE.add(toleranceFraction));
    BigDecimal lowerBound = targetDrr.multiply(BigDecimal.ONE.subtract(toleranceFraction));

    int currentBid = signals.currentBid();
    BigDecimal drrPct = signals.drrPct();

    if (drrPct.compareTo(upperBound) > 0) {
      return decideBidDown(signals, currentBid, stepDown, targetDrr, tolerance);
    }

    if (drrPct.compareTo(lowerBound) < 0 && roasAboveMin(signals, minRoas)) {
      return decideBidUp(signals, currentBid, stepUp, maxBidKopecks, targetDrr, tolerance);
    }

    return holdWithExplanation(signals, currentBid, targetDrr, tolerance);
  }

  private BiddingStrategyResult decideBidDown(
      BiddingSignalSet signals, int currentBid,
      BigDecimal stepDown, BigDecimal targetDrr, BigDecimal tolerance) {

    BigDecimal factor = BigDecimal.ONE.subtract(
        stepDown.divide(HUNDRED, 4, RoundingMode.HALF_UP));
    int rawTarget = Math.round(currentBid * factor.floatValue());
    int target = clampBid(rawTarget, signals.minBid());

    return new BiddingStrategyResult(
        BidDecisionType.BID_DOWN, target,
        explanation(signals.drrPct(), targetDrr, tolerance, "BID_DOWN", currentBid, target));
  }

  private BiddingStrategyResult decideBidUp(
      BiddingSignalSet signals, int currentBid,
      BigDecimal stepUp, Integer maxBidKopecks,
      BigDecimal targetDrr, BigDecimal tolerance) {

    BigDecimal factor = BigDecimal.ONE.add(
        stepUp.divide(HUNDRED, 4, RoundingMode.HALF_UP));
    int rawTarget = Math.round(currentBid * factor.floatValue());

    if (maxBidKopecks != null && rawTarget > maxBidKopecks) {
      rawTarget = maxBidKopecks;
    }
    int target = clampBid(rawTarget, signals.minBid());

    return new BiddingStrategyResult(
        BidDecisionType.BID_UP, target,
        explanation(signals.drrPct(), targetDrr, tolerance, "BID_UP", currentBid, target));
  }

  private BiddingStrategyResult holdWithExplanation(
      BiddingSignalSet signals, int currentBid,
      BigDecimal targetDrr, BigDecimal tolerance) {

    return new BiddingStrategyResult(
        BidDecisionType.HOLD, currentBid,
        explanation(signals.drrPct(), targetDrr, tolerance, "HOLD", currentBid, currentBid));
  }

  private BiddingStrategyResult hold(BiddingSignalSet signals, String reason) {
    return new BiddingStrategyResult(
        BidDecisionType.HOLD, signals.currentBid(),
        reason);
  }

  private boolean roasAboveMin(BiddingSignalSet signals, BigDecimal minRoas) {
    return signals.roas() != null && signals.roas().compareTo(minRoas) > 0;
  }

  private int clampBid(int rawTarget, Integer minBid) {
    int floor = minBid != null ? Math.max(minBid, WB_MIN_BID_KOPECKS) : WB_MIN_BID_KOPECKS;
    return Math.max(rawTarget, floor);
  }

  private String explanation(
      BigDecimal drrPct, BigDecimal targetDrr, BigDecimal tolerance,
      String decision, int currentBid, int targetBid) {

    return "DRR %s%% vs target %s%%±%s%%. Decision: %s. Bid: %d → %d kopecks"
        .formatted(
            drrPct.setScale(1, RoundingMode.HALF_UP).toPlainString(),
            targetDrr.setScale(1, RoundingMode.HALF_UP).toPlainString(),
            tolerance.setScale(0, RoundingMode.HALF_UP).toPlainString(),
            decision, currentBid, targetBid);
  }

  private BigDecimal decimalField(JsonNode config, String fieldName, BigDecimal defaultValue) {
    if (config == null || !config.has(fieldName) || config.get(fieldName).isNull()) {
      return defaultValue;
    }
    return config.get(fieldName).decimalValue();
  }

  private Integer intField(JsonNode config, String fieldName) {
    if (config == null || !config.has(fieldName) || config.get(fieldName).isNull()) {
      return null;
    }
    return config.get(fieldName).intValue();
  }
}
