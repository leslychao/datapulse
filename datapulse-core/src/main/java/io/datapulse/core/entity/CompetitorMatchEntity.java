package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "competitor_match")
@Getter
@Setter
public class CompetitorMatchEntity extends LongBaseEntity {

  @ManyToOne
  @JoinColumn(name = "account_id")
  private AccountEntity account;
  private Long productId;
  private String competitorSku;
  private String competitorName;
  private String competitorBrand;
  private BigDecimal competitorPrice;
  private Boolean verified;
}
