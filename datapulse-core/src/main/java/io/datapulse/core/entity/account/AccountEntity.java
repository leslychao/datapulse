package io.datapulse.core.entity.account;

import io.datapulse.core.entity.LongBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account")
@Getter
@Setter
public class AccountEntity extends LongBaseEntity {

  private String name;
  private boolean active;
}
