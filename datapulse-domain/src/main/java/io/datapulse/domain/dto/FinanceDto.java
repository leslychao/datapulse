package io.datapulse.domain.dto;

import io.datapulse.domain.OperationType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceDto extends LongBaseDto {

  private String operationId;
  private OperationType operationType;
  private OffsetDateTime operationDate;
  private String postingNumber;
  private BigDecimal amountTotal;
  private BigDecimal commissionAmount;
  private BigDecimal deliveryAmount;
  private BigDecimal storageFeeAmount;
  private BigDecimal penaltyAmount;
  private BigDecimal marketingAmount;
  private String currency;
}
