package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stock_snapshot")
@Getter
@Setter
public class StockSnapshotEntity extends LongBaseEntity {

  private Long accountId;
  private Long productId;
  private LocalDate date;
  private Integer stock;
  private Boolean outOfStockFlag;
  private Integer daysOfCover;
}
