package io.datapulse.domain.dto;

import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class StockDto extends LongBaseDto {

  private Long accountId;
  private Long productId;
  private LocalDate date;
  private Integer stock;
  private Boolean outOfStockFlag;
  private Integer daysOfCover;
}
