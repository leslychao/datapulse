package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.util.List;

public record ReconciliationResultResponse(
    List<ConnectionReconciliation> connections,
    List<TrendPoint> trend,
    List<ResidualBucket> distribution
) {

  public record ConnectionReconciliation(
      long connectionId,
      String connectionName,
      String marketplaceType,
      BigDecimal residualAmount,
      BigDecimal residualRatioPct,
      BigDecimal baselineRatioPct,
      String status
  ) {}

  public record TrendPoint(
      String period,
      long connectionId,
      BigDecimal residualRatioPct,
      BigDecimal baselineRatioPct
  ) {}

  public record ResidualBucket(
      String label,
      int count,
      BigDecimal from,
      BigDecimal to
  ) {}
}
