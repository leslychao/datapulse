package io.datapulse.core.entity;

import io.datapulse.domain.MarketplaceType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account")
@Getter
@Setter
public class AccountEntity extends LongBaseEntity {

  @Enumerated(EnumType.STRING)
  private MarketplaceType marketplace;
  private String name;
  private String tokenEncrypted;
  private String login;
}
