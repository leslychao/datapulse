package io.datapulse.domain.dto.productcost;

import io.datapulse.domain.dto.LongBaseDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ProductCostDto extends LongBaseDto {

  private Long accountId;
  private Long productId;

  private BigDecimal costValue;
  private String currency;

  private LocalDate validFrom;
  private LocalDate validTo;

  private OffsetDateTime createdAt;
}
