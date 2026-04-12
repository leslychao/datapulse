package io.datapulse.bidding.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "datapulse.bidding")
public class BiddingProperties {

  @Min(1)
  private final int defaultLookbackDays;

  @Min(1)
  private final int maxBidUpRatioPct;

  @Min(1)
  private final int minDecisionIntervalHours;

  @Min(1)
  private final int staleDataThresholdHours;

  @Min(1)
  private final int lowStockThresholdDays;

  private final boolean enabled;

  @Min(1)
  private final int volatilityMaxReversals;

  @Min(1)
  private final int volatilityPeriodDays;

  @Min(0)
  private final int maxAbsChangePct;

  public BiddingProperties() {
    this(7, 50, 4, 48, 7, true, 3, 7, 30);
  }
}
