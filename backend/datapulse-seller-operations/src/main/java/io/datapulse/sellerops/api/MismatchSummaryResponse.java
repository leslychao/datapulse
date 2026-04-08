package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.util.List;

public record MismatchSummaryResponse(
    long totalActive,
    long totalActiveDelta7d,
    long criticalCount,
    long criticalDelta7d,
    BigDecimal avgHoursUnresolved,
    BigDecimal avgHoursUnresolvedDelta7d,
    long autoResolvedToday,
    long autoResolvedYesterday,
    List<TypeDistribution> distributionByType,
    List<TimelinePoint> timeline
) {

  public record TypeDistribution(String type, long count) {
  }

  public record TimelinePoint(String date, long newCount, long resolvedCount) {
  }
}

