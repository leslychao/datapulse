package io.datapulse.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class SaleDto extends LongBaseDto {

  private Long accountId;
  private Long productId;
  private LocalDate date;
  private Integer quantity;
  private BigDecimal revenue;
  private BigDecimal cost;
  private BigDecimal margin;
}
