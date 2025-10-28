package io.datapulse.domain.dto;

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
public class StockDto extends LongBaseDto {

  /**
   * Унифицированный идентификатор товара: WB.nmId / OZON.sku (строка для единообразия)
   */
  private String sku;

  /**
   * Артикул продавца: WB.supplierArticle / OZON.offer_id
   */
  private String offerId;

  /**
   * Идентификатор товара площадки: WB.nmId / OZON.product_id (как строка)
   */
  private String productId;

  /**
   * Склад: идентификатор и (если есть) имя
   */
  private String warehouseId;
  private String warehouseName;

  /**
   * Остатки
   */
  private Integer quantityAvailable;   // доступно к продаже: WB.quantity / OZON.present
  private Integer quantityReserved;    // в резерве: WB.inWayToClient? (резерв у WB — нет), OZON.reserved
  private Integer quantityInTransitToClient;   // WB.inWayToClient (в пути к клиенту)
  private Integer quantityInTransitFromClient; // WB.inWayFromClient (возвраты в пути)
  private Integer quantityTotal;       // общий расчётный остаток (см. мапперы)

  /**
   * Атрибуты товара
   */
  private String barcode;
  private String category;
  private String subject;
  private String brand;
  private String techSize;

  /**
   * Цена и скидка (если источник отдаёт)
   */
  private BigDecimal priceOriginal;
  private Integer discountPercent;

  /**
   * Время последнего изменения (если есть)
   */
  private OffsetDateTime lastChangeDate;
}
