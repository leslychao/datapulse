package io.datapulse.domain.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonStockRaw(
    Result result
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      List<Item> items
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Item(
      String offer_id,
      Long product_id,
      Long sku,
      List<Stock> stocks
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Stock(
      Long warehouse_id,
      Integer present,
      Integer reserved,
      Integer reserved_pickup,
      Integer shipped // встречается не всегда; игнор лишних ок
  ) {}
}
