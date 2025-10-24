package io.datapulse.adapter.marketplaces.dto.wb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** /public/api/sales — продажи */
public record WbSalesResponse(
    List<Item> data,
    String nextCursor // если используется постраничность курсором
) {
  public record Item(
      String sku,
      LocalDate date,
      Integer quantity,
      BigDecimal revenue, // forPay/forPayment аналоги приводим к revenue
      BigDecimal cost     // если WB даёт, иначе null
  ) {}
}

