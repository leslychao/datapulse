package io.datapulse.core.entity.productcost;

import io.datapulse.core.entity.LongBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "fact_product_cost")
public class ProductCostEntity extends LongBaseEntity {

  private Long accountId;
  private Long productId;

  private BigDecimal costValue;
  private String currency;

  private LocalDate validFrom;
  private LocalDate validTo;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
