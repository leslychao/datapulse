package io.datapulse.pricing.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strategy-agnostic extraction of rounding parameters from any strategy params JSON.
 * All strategy params records (TargetMarginParams, VelocityAdaptiveParams,
 * StockBalancingParams, CompositeParams, CompetitorAnchorParams) share the same
 * {@code roundingStep} / {@code roundingDirection} fields — this record
 * lets the constraint resolver parse them without knowing the concrete type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoundingConfig(
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {

  public BigDecimal effectiveRoundingStep() {
    return roundingStep != null ? roundingStep : BigDecimal.TEN;
  }

  public TargetMarginParams.RoundingDirection effectiveRoundingDirection() {
    return roundingDirection != null
        ? roundingDirection : TargetMarginParams.RoundingDirection.FLOOR;
  }
}
