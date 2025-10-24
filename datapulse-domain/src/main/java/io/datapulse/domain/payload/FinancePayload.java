package io.datapulse.domain.payload;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class FinancePayload {
  private long accountId;
  private LocalDate date;
  private String itemType;
  private BigDecimal amount;
}
