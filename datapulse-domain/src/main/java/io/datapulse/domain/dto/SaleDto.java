package io.datapulse.domain.dto;

import io.datapulse.domain.FulfillmentType;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class SaleDto extends LongBaseDto {

  private String sku;
  private String postingNumber;
  private String offerId;
  private FulfillmentType fulfillment;
  private String status;
  private Boolean isCancelled;
  private Boolean isReturn;

  private LocalDate eventDate;
  private OffsetDateTime createdAt;
  private OffsetDateTime processedAt;

  private Integer quantity;
  private BigDecimal priceOriginal;
  private BigDecimal priceFinal;
  private BigDecimal revenue;
  private BigDecimal cost;
  private BigDecimal commissionAmount;
  private BigDecimal deliveryAmount;
  private BigDecimal storageFeeAmount;
  private BigDecimal penaltyAmount;
  private BigDecimal marketingAmount;
}
