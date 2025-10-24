package io.datapulse.domain.payload;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalePayload {

  private long accountId;
  private LocalDate date;
  private String sku;
  private String offerId;
  private int quantity;
  private BigDecimal revenue;
  private BigDecimal price;
  private String source;
}
