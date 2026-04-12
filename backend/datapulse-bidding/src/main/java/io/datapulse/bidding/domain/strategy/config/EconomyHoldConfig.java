package io.datapulse.bidding.domain.strategy.config;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EconomyHoldConfig(
    @NotNull @DecimalMin("0.01") BigDecimal targetDrrPct,
    @DecimalMin("1") @Max(100) BigDecimal tolerancePct,
    @DecimalMin("1") @Max(100) BigDecimal stepUpPct,
    @DecimalMin("1") @Max(100) BigDecimal stepDownPct,
    @DecimalMin("0.1") BigDecimal minRoas,
    @Min(1) Integer maxBidKopecks
) {

  public EconomyHoldConfig {
    if (tolerancePct == null) tolerancePct = BigDecimal.TEN;
    if (stepUpPct == null) stepUpPct = BigDecimal.TEN;
    if (stepDownPct == null) stepDownPct = BigDecimal.valueOf(15);
    if (minRoas == null) minRoas = BigDecimal.ONE;
  }
}
