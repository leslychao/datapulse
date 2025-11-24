package io.datapulse.marketplaces.dto.normalized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record OzonSalesAnalyticsRawDto(
    String productId,
    String offerId,
    String warehouseId,
    LocalDate day,
    List<MetricEntry> metrics
) {

  public record MetricEntry(
      String id,
      BigDecimal value
  ) {
  }
}

