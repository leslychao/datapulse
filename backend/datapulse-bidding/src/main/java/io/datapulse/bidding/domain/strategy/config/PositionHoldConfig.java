package io.datapulse.bidding.domain.strategy.config;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionHoldConfig(
    @NotNull @Min(1) Long targetImpressionsDaily,
    @DecimalMin("1") BigDecimal impressionsTolerancePct,
    @DecimalMin("0.1") BigDecimal ceilingDrrPct,
    @DecimalMin("1") BigDecimal bidStepPct,
    @Min(1) Integer lookbackDays
) {

  public PositionHoldConfig {
    if (impressionsTolerancePct == null) {
      impressionsTolerancePct = BigDecimal.valueOf(20);
    }
    if (bidStepPct == null) bidStepPct = BigDecimal.TEN;
  }
}
