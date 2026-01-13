package io.datapulse.domain.response.inventory;

import java.time.LocalDate;
import lombok.Data;

@Data
public class InventorySnapshotResponse {

  private Long id;

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
}
