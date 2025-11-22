package io.datapulse.core.entity;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.SalesFactDto.SalesOperationType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sales_fact")
@EqualsAndHashCode(callSuper = true)
public final class SalesFactEntity extends LongBaseEntity {

  private long accountId;
  @Enumerated(EnumType.STRING)
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

  @Enumerated(EnumType.STRING)
  private SalesOperationType operationType;

  private String externalOperationId;
  private String currencyCode;
}
