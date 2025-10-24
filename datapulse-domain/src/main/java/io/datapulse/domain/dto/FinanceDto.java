package io.datapulse.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class FinanceDto extends LongBaseDto {
  private Long accountId;
  private LocalDate date;
  private String feeType;
  private BigDecimal amount;
}