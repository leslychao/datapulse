package io.datapulse.core.entity.account.invite;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.AccountInviteStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "account_invite")
@Getter
@Setter
public class AccountInviteEntity extends LongBaseEntity {

  private String email;

  private String tokenHash;

  @Enumerated(EnumType.STRING)
  private AccountInviteStatus status;

  private OffsetDateTime expiresAt;

  private long createdByProfileId;
}
