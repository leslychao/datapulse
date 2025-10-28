package io.datapulse.adapter.marketplaces.dto.response.wb;

import java.time.LocalDate;
import java.util.List;

public record WbStockResponse(List<Item> data, String nextCursor) {

  public record Item(String sku, LocalDate date, Integer stock, String warehouseCode) {

  }
}
