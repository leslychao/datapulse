package io.datapulse.bidding.domain.strategy.config;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowthConfig(
    @NotNull @DecimalMin("0.01") BigDecimal targetCpo,
    @NotNull @DecimalMin("0.01") BigDecimal maxCpo,
    @Min(1) Integer minClicksForSignal,
    @DecimalMin("1") BigDecimal bidStepPct,
    @Min(1) Integer maxBid
) {

  public GrowthConfig {
    if (minClicksForSignal == null) minClicksForSignal = 10;
    if (bidStepPct == null) bidStepPct = BigDecimal.TEN;
  }
}
