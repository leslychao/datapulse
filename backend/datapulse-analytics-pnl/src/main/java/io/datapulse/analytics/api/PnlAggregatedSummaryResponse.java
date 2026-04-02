package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.util.List;

public record PnlAggregatedSummaryResponse(
    BigDecimal revenueAmount,
    BigDecimal totalCostsAmount,
    BigDecimal cogsAmount,
    BigDecimal advertisingCostAmount,
    BigDecimal marketplacePnl,
    BigDecimal fullPnl,
    BigDecimal reconciliationResidual,
    BigDecimal reconciliationRatio,
    BigDecimal revenueDeltaPct,
    BigDecimal costsDeltaPct,
    BigDecimal cogsDeltaPct,
    BigDecimal advertisingDeltaPct,
    BigDecimal pnlDeltaPct,
    List<CostBreakdownItem> costBreakdown
) {

  public record CostBreakdownItem(
      String category,
      BigDecimal amount,
      BigDecimal percent
  ) {}
}
