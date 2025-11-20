package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account")
@Getter
@Setter
public class AccountEntity extends LongBaseEntity {

  private String name;
  private OffsetDateTime createdAt = OffsetDateTime.now();
  private OffsetDateTime updatedAt;
  private boolean active;
}
