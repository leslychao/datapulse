package io.datapulse.core.entity.account.invite;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.AccountMemberRole;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account_invite_target")
@Getter
@Setter
public class AccountInviteTargetEntity extends LongBaseEntity {

  private long inviteId;

  private long accountId;

  @Enumerated(EnumType.STRING)
  private AccountMemberRole initialRole;
}
