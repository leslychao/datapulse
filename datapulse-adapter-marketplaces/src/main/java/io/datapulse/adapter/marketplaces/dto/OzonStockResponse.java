package io.datapulse.adapter.marketplaces.dto;

import java.util.List;

public record OzonStockResponse(List<OzonStockItem> result) {

  public record OzonStockItem(String product_id, Integer free_to_sell_amount) {

  }
}
