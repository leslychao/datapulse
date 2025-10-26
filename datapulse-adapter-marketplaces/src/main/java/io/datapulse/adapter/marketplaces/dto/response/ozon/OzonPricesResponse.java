package io.datapulse.adapter.marketplaces.dto.response.ozon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OzonPricesResponse(List<Item> result) {

  public record Item(
      String sku,
      LocalDate date,
      BigDecimal price,
      BigDecimal promoPrice,
      Boolean promoActive) {

  }
}
