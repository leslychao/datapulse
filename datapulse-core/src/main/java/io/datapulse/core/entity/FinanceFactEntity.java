package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "finance_fact")
@Getter
@Setter
public class FinanceFactEntity extends LongBaseEntity {

  private Long accountId;
  private LocalDate date;
  private String feeType;
  private BigDecimal amount;
}
