package io.datapulse.core.entity.inventory;

import io.datapulse.core.entity.LongBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "fact_inventory_snapshot")
public class FactInventorySnapshotEntity extends LongBaseEntity {

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
