package io.datapulse.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class PriceDto extends LongBaseDto {

  private Long accountId;
  private Long productId;
  private LocalDate date;
  private BigDecimal price;
  private BigDecimal promoPrice;
  private Boolean promoActive;
}
