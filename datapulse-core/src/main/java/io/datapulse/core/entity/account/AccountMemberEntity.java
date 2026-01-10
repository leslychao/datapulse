package io.datapulse.core.entity.account;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.entity.UserProfileEntity;
import io.datapulse.domain.AccountMemberRole;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account_member")
@Getter
@Setter
public class AccountMemberEntity extends LongBaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private AccountEntity account;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserProfileEntity user;

  @Enumerated(EnumType.STRING)
  private AccountMemberRole role;

  private OffsetDateTime createdAt = OffsetDateTime.now();
  private OffsetDateTime updatedAt;
}
