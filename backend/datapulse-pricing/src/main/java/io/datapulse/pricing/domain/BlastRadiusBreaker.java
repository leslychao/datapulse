package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aggregate circuit breaker for FULL_AUTO pricing runs.
 * Tracks two metrics and pauses the run if either exceeds its threshold:
 * <ul>
 *   <li>{@code change_ratio} — share of offers with |price_change_pct| &gt; 5% among eligible</li>
 *   <li>{@code max_abs_change_pct} — largest |price_change_pct| across all CHANGE decisions</li>
 * </ul>
 */
@Component
public class BlastRadiusBreaker {

  private static final BigDecimal SIGNIFICANT_CHANGE_THRESHOLD = new BigDecimal("0.05");

  @Value("${datapulse.pricing.blast-radius.change-ratio-threshold:0.30}")
  private BigDecimal changeRatioThreshold;

  @Value("${datapulse.pricing.blast-radius.max-abs-change-pct:0.25}")
  private BigDecimal maxAbsChangePctThreshold;

  private int significantChanges;
  private int totalProcessed;
  private BigDecimal maxAbsChangePct;

  public void reset() {
    significantChanges = 0;
    totalProcessed = 0;
    maxAbsChangePct = BigDecimal.ZERO;
  }

  public void recordDecision(BigDecimal currentPrice, BigDecimal targetPrice) {
    totalProcessed++;
    if (currentPrice == null || targetPrice == null
        || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
      return;
    }
    BigDecimal changePct = targetPrice.subtract(currentPrice)
        .divide(currentPrice, 4, RoundingMode.HALF_UP).abs();
    if (changePct.compareTo(SIGNIFICANT_CHANGE_THRESHOLD) > 0) {
      significantChanges++;
    }
    if (changePct.compareTo(maxAbsChangePct) > 0) {
      maxAbsChangePct = changePct;
    }
  }

  public boolean isBreached() {
    if (totalProcessed == 0) {
      return false;
    }
    return currentRatio().compareTo(changeRatioThreshold) > 0
        || maxAbsChangePct.compareTo(maxAbsChangePctThreshold) > 0;
  }

  public String breachedMetric() {
    if (currentRatio().compareTo(changeRatioThreshold) > 0) {
      return "change_ratio";
    }
    if (maxAbsChangePct.compareTo(maxAbsChangePctThreshold) > 0) {
      return "max_abs_change_pct";
    }
    return null;
  }

  public BigDecimal currentRatio() {
    if (totalProcessed == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(significantChanges)
        .divide(BigDecimal.valueOf(totalProcessed), 4, RoundingMode.HALF_UP);
  }

  public BigDecimal currentMaxAbsChangePct() {
    return maxAbsChangePct;
  }
}
