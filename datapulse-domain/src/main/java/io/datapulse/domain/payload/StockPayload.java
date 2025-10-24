package io.datapulse.domain.payload;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StockPayload {
  private long accountId;
  private LocalDate date;
  private String sku;
  private String offerId;
  private int stockAvailable;
  private boolean outOfStock;
}
