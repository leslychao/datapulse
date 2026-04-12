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
 * LAUNCH — initial traffic acquisition for new products.
 *
 * Logic:
 *   - During launch period: do not lower bid even at high DRR
 *     (unless DRR exceeds ceiling)
 *   - If clicks < min target at the end of the period: extend once
 *   - After launch period ends: recommend transition to targetStrategy
 */
@Component
public class LaunchStrategy implements BiddingStrategy {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int DEFAULT_MIN_BID = 50;
  private static final int DEFAULT_LAUNCH_PERIOD_DAYS = 7;
  private static final int DEFAULT_MIN_CLICKS_TARGET = 50;
  private static final BigDecimal DEFAULT_CEILING_DRR = BigDecimal.valueOf(30);

  @Override
  public BiddingStrategyType strategyType() {
    return BiddingStrategyType.LAUNCH;
  }

  @Override
  public BiddingStrategyResult evaluate(
      BiddingSignalSet signals, JsonNode policyConfig) {

    if (signals.currentBid() == null) {
      Integer startingBid = intFieldNullable(policyConfig, "startingBid");
      if (startingBid != null) {
        return new BiddingStrategyResult(BidDecisionType.BID_UP,
            startingBid,
            "Launch: no current bid, setting starting bid %d"
                .formatted(startingBid));
      }
      if (signals.competitiveBid() != null) {
        return new BiddingStrategyResult(BidDecisionType.BID_UP,
            signals.competitiveBid(),
            "Launch: no current bid, using competitive bid %d"
                .formatted(signals.competitiveBid()));
      }
      return BiddingStrategyResult.hold(
          "Launch: no current bid and no starting bid configured");
    }

    int launchPeriodDays = intField(policyConfig, "launchPeriodDays",
        DEFAULT_LAUNCH_PERIOD_DAYS);
    int minClicksTarget = intField(policyConfig, "minClicksTarget",
        DEFAULT_MIN_CLICKS_TARGET);
    BigDecimal ceilingDrr = decimalField(policyConfig, "ceilingDrrPct",
        DEFAULT_CEILING_DRR);

    int currentBid = signals.currentBid();
    Integer hoursSinceChange = signals.hoursSinceLastChange();
    boolean withinLaunchPeriod = hoursSinceChange == null
        || hoursSinceChange < launchPeriodDays * 24;

    if (withinLaunchPeriod) {
      if (signals.drrPct() != null
          && signals.drrPct().compareTo(ceilingDrr) > 0) {
        int floor = signals.minBid() != null
            ? Math.max(signals.minBid(), DEFAULT_MIN_BID)
            : DEFAULT_MIN_BID;
        int target = Math.max(
            Math.round(currentBid * 0.9f), floor);
        return new BiddingStrategyResult(BidDecisionType.BID_DOWN, target,
            "Launch: within period but DRR %s%% exceeds ceiling %s%%. "
                .formatted(
                    signals.drrPct().setScale(1, RoundingMode.HALF_UP),
                    ceilingDrr.setScale(1, RoundingMode.HALF_UP))
                + "Moderate bid down: %d → %d"
                    .formatted(currentBid, target));
      }

      int daysSinceChange = hoursSinceChange != null ? hoursSinceChange / 24 : 0;
      return new BiddingStrategyResult(BidDecisionType.HOLD, currentBid,
          "Launch: within period (day %s of %d), holding bid at %d"
              .formatted(
                  hoursSinceChange != null ? String.valueOf(daysSinceChange) : "?",
                  launchPeriodDays, currentBid));
    }

    if (signals.clicks() < minClicksTarget) {
      return new BiddingStrategyResult(BidDecisionType.HOLD, currentBid,
          "Launch: period ended but clicks %d < target %d. "
              .formatted(signals.clicks(), minClicksTarget)
              + "Extending launch period, holding bid");
    }

    String targetStrategy = stringField(policyConfig, "targetStrategy",
        "ECONOMY_HOLD");
    BiddingStrategyType transitionTarget =
        BiddingStrategyType.valueOf(targetStrategy);
    return new BiddingStrategyResult(BidDecisionType.HOLD, currentBid,
        "Launch: period completed, clicks %d >= target %d. "
            .formatted(signals.clicks(), minClicksTarget)
            + "Auto-transitioning to %s".formatted(targetStrategy),
        transitionTarget);
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

  private String stringField(JsonNode cfg, String field, String def) {
    if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) {
      return def;
    }
    return cfg.get(field).asText();
  }
}
