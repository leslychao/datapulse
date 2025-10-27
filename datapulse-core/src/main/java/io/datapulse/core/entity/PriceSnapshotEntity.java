package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "price_snapshot")
@Getter
@Setter
public class PriceSnapshotEntity extends LongBaseEntity {

  @ManyToOne
  @JoinColumn(name = "account_id")
  private AccountEntity account;
  private Long productId;
  private LocalDate date;
  private BigDecimal price;
  private BigDecimal promoPrice;
  private Boolean promoActive;
}
