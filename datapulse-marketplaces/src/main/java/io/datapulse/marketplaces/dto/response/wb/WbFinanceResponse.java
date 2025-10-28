package io.datapulse.marketplaces.dto.response.wb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WbFinanceResponse(List<Item> data, String nextCursor) {

  public record Item(LocalDate date, String feeType, BigDecimal amount) {

  }
}
