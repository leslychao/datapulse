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
@Table(name = "finance_fact")
@Getter
@Setter
public class FinanceFactEntity extends LongBaseEntity {

  @ManyToOne
  @JoinColumn(name = "account_id")
  private AccountEntity account;
  private LocalDate date;
  private String feeType;
  private BigDecimal amount;
}
