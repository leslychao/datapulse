package io.datapulse.adapter.marketplaces.dto.ozon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OzonSalesResponse(List<Item> result, String pagingToken) {

  public record Item(
      String sku,
      LocalDate date,
      Integer quantity,
      BigDecimal revenue,
      BigDecimal cost) {

  }
}
