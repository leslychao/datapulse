package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sales_fact")
@Getter
@Setter
public class SaleFactEntity extends LongBaseEntity {

  private Long accountId;
  private Long productId;
  private LocalDate date;
  private Integer quantity;
  private BigDecimal revenue;
  private BigDecimal cost;
  private BigDecimal margin;
}
