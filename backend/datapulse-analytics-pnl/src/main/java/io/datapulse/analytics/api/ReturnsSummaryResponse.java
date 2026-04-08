package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.util.List;

public record ReturnsSummaryResponse(
    BigDecimal returnRatePct,
    BigDecimal returnRateDeltaPct,
    BigDecimal totalReturnAmount,
    int totalReturnCount,
    String topReturnReason,
    List<ReasonBreakdownItem> reasonBreakdown
) {

  public record ReasonBreakdownItem(
      String reason,
      int count,
      BigDecimal percent
  ) {}
}
