package io.datapulse.adapter.marketplaces.dto.response.ozon;

import java.time.LocalDate;
import java.util.List;

public record OzonStockResponse(List<Item> result, String pagingToken) {

  public record Item(String sku, LocalDate date, Integer stock, String warehouseCode) {

  }
}
