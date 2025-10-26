package io.datapulse.adapter.marketplaces.dto.response.wb;

import java.time.LocalDate;
import java.util.List;

/**
 * /public/api/stock — остатки
 */
public record WbStockResponse(List<Item> data, String nextCursor) {

  public record Item(String sku, LocalDate date, Integer stock, String warehouseCode) {

  }
}
