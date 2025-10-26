package io.datapulse.adapter.marketplaces.dto.response.ozon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OzonFinanceResponse(List<Item> result, String pagingToken) {

  public record Item(LocalDate date, String feeType, BigDecimal amount) {

  }
}
