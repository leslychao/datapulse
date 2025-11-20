package io.datapulse.domain.dto;

import io.datapulse.domain.MarketplaceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SalesFactDto(

    long accountId,
    MarketplaceType marketplace,

    LocalDate operationDate,
    OffsetDateTime operationDateTime,

    String offerId,
    String barcode,
    String size,
    String warehouseName,
    String regionName,

    int quantity,

    BigDecimal grossAmount,
    BigDecimal commissionAmount,
    BigDecimal logisticsAndFeesAmount,
    BigDecimal promoAmount,
    BigDecimal netAmount,

    SalesOperationType operationType,
    String externalOperationId,
    String currencyCode
) {

  public enum SalesOperationType {
    SALE,
    RETURN,
    CANCEL,
    OTHER
  }
}
