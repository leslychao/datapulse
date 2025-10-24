package io.datapulse.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AdSpendDto extends LongBaseDto {
  private Long accountId;
  private LocalDate date;
  private BigDecimal shows;
  private BigDecimal clicks;
  private BigDecimal ctr;
  private BigDecimal cpc;
  private BigDecimal cpo;
  private BigDecimal roas;
}
