package io.datapulse.core.entity;

import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account_member")
@Getter
@Setter
public class AccountMemberEntity extends LongBaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private AccountEntity account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserProfileEntity user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id", nullable = false)
  private UserProfileEntity createdBy;

  @Enumerated(EnumType.STRING)
  private AccountMemberRole role;

  @Enumerated(EnumType.STRING)
  private AccountMemberStatus status = AccountMemberStatus.ACTIVE;
}
