package io.datapulse.adapter.marketplaces.dto;

import java.math.BigDecimal;
import java.util.List;

public record WbSalesResponse(List<WbSaleItem> data) {

  public record WbSaleItem(String nmId, String date, Integer quantity, BigDecimal forPay) {

  }
}
