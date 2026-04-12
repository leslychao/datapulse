package io.datapulse.bidding.domain.strategy.config;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LaunchConfig(
    @Min(1) Integer startingBid,
    @Min(1) Integer launchPeriodDays,
    @Min(1) Integer minClicksTarget,
    @DecimalMin("1") BigDecimal ceilingDrrPct,
    String targetStrategy
) {

  public LaunchConfig {
    if (launchPeriodDays == null) launchPeriodDays = 7;
    if (minClicksTarget == null) minClicksTarget = 50;
    if (ceilingDrrPct == null) ceilingDrrPct = BigDecimal.valueOf(30);
    if (targetStrategy == null) targetStrategy = "ECONOMY_HOLD";
  }
}
