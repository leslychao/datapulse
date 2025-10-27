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
@Table(name = "ad_spend_fact")
@Getter
@Setter
public class AdSpendFactEntity extends LongBaseEntity {

  @ManyToOne
  @JoinColumn(name = "account_id")
  private AccountEntity account;
  private LocalDate date;
  private BigDecimal shows;
  private BigDecimal clicks;
  private BigDecimal ctr;
  private BigDecimal cpc;
  private BigDecimal cpo;
  private BigDecimal roas;
}
