package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_profile")
@Getter
@Setter
public class UserProfileEntity extends LongBaseEntity {

  private String keycloakSub;
  private String email;
  private String fullName;
  private String username;

  private OffsetDateTime createdAt = OffsetDateTime.now();
  private OffsetDateTime updatedAt;
}
