package io.datapulse.domain.dto;

import io.datapulse.domain.FulfillmentType;
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
public class StockDto extends LongBaseDto {

  private String sku;
  private LocalDate onDate;
  private OffsetDateTime capturedAt;
  private String warehouseId;
  private String warehouseName;
  private FulfillmentType fulfillment;
  private Integer qtyAvailable;
  private Integer qtyReserved;
  private Integer qtyInTransit;
  private Integer daysOfCover;
}
