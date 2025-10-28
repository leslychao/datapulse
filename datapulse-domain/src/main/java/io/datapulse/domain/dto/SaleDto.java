package io.datapulse.domain.dto;

import io.datapulse.domain.FulfillmentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SaleDto extends LongBaseDto {

  /** SKU / nmId / sku (строковый унификатор) */
  private String sku;

  /** Номер отправления/заказа (Ozon: posting_number; WB: gNumber/saleID/srid — что есть) */
  private String postingNumber;

  /** Offer Id (Ozon: offer_id; WB: supplierArticle) */
  private String offerId;

  /** FBO/FBS/rFBS (Ozon: delivery_schema; WB: srv_dbs ? → DBS/FBS; можно оставить null) */
  private FulfillmentType fulfillment;

  /** Статус (агрегированный: например, «sold», «return») */
  private String status;

  private Boolean isCancelled;
  private Boolean isReturn;

  /** Календарная дата события (Ozon: по агрегату — date; WB: date) */
  private LocalDate eventDate;

  /** Создано/обработано (если есть) */
  private OffsetDateTime createdAt;
  private OffsetDateTime processedAt;

  private Integer quantity;

  /** Цены/выручка/затраты (где есть) */
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
