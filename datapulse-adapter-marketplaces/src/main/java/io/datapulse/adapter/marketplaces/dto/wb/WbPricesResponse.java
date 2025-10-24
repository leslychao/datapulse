package io.datapulse.adapter.marketplaces.dto.wb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WbPricesResponse(List<Item> data) {

  public record Item(
      String sku,
      LocalDate date,
      BigDecimal price,
      BigDecimal promoPrice,
      Boolean promoActive) {
  }
}
