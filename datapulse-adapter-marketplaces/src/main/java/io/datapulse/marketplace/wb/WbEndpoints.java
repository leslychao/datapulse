package io.datapulse.marketplace.wb;

import java.net.URI;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class WbEndpoints {

  public String resolveTokenForAccount(Long accountId) {
    return "Bearer ***";
  }

  public URI sales(LocalDate fromInclusive, LocalDate toInclusive) {
    return URI.create(
        "https://seller.wb.ru/api/v1/sales?dateFrom=" + fromInclusive + "&dateTo=" + toInclusive);
  }
}
