package io.datapulse.domain.dto.inventory;

import io.datapulse.domain.dto.LongBaseDto;
import java.time.Instant;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class InventorySnapshotDto extends LongBaseDto {

  private Long accountId;
  private String sourcePlatform;
  private LocalDate snapshotDate;
  private String sourceProductId;
  private Long warehouseId;

  private Integer quantityTotal;
  private Integer quantityAvailable;
  private Integer quantityReserved;
  private Integer quantityInWayToClient;
  private Integer quantityInWayFromClient;
  private Integer quantityReturnToSeller;
  private Integer quantityReturnFromCustomer;

  private Instant createdAt;
  private Instant updatedAt;
}
