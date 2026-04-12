package io.datapulse.bidding.domain.strategy.config;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LiquidationConfig(
    @DecimalMin("1") BigDecimal maxDrrPct,
    @DecimalMin("1") BigDecimal bidStepPct,
    @Min(1) Integer exitDaysOfCover
) {

  public LiquidationConfig {
    if (maxDrrPct == null) maxDrrPct = BigDecimal.valueOf(25);
    if (bidStepPct == null) bidStepPct = BigDecimal.valueOf(15);
    if (exitDaysOfCover == null) exitDaysOfCover = 7;
  }
}
