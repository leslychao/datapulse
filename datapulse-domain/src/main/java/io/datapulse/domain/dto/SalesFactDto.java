package io.datapulse.domain.dto;

import io.datapulse.domain.MarketplaceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class SalesFactDto extends LongBaseDto {

  private Long id;

  private long accountId;
  private MarketplaceType marketplace;

  private LocalDate operationDate;
  private OffsetDateTime operationDateTime;

  private String offerId;
  private String barcode;
  private String size;
  private String warehouseName;
  private String regionName;

  private int quantity;

  private BigDecimal grossAmount;
  private BigDecimal commissionAmount;
  private BigDecimal logisticsAndFeesAmount;
  private BigDecimal promoAmount;
  private BigDecimal netAmount;

  private SalesOperationType operationType;
  private String externalOperationId;
  private String currencyCode;

  public enum SalesOperationType {
    SALE,
    RETURN,
    CANCEL,
    OTHER
  }
}
