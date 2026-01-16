package io.datapulse.iam.authorization;

import io.datapulse.core.entity.AccountMemberEntity;
import io.datapulse.core.repository.AccountMemberRepository;
import io.datapulse.domain.AccountMemberStatus;
import io.datapulse.domain.exception.SecurityException;
import io.datapulse.iam.DomainUserContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAccessService {

  private final DomainUserContext domainUserContext;
  private final AccountMemberRepository accountMemberRepository;

  @Transactional(readOnly = true)
  public boolean canRead(long accountId) {
    return findActiveMember(accountId)
        .map(member -> AccountRoleCapabilities.canRead(member.getRole()))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean canWrite(long accountId) {
    return findActiveMember(accountId)
        .map(member -> AccountRoleCapabilities.canWrite(member.getRole()))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean canManageMembers(long accountId) {
    return findActiveMember(accountId)
        .map(member -> AccountRoleCapabilities.canManageMembers(member.getRole()))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean canDeleteAccount(long accountId) {
    return findActiveMember(accountId)
        .map(member -> AccountRoleCapabilities.canDeleteAccount(member.getRole()))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public void requireRead(long accountId) {
    AccountMemberEntity member = requireActiveMember(accountId);
    if (!AccountRoleCapabilities.canRead(member.getRole())) {
      throw SecurityException.accessDeniedForAccount(accountId);
    }
  }

  @Transactional(readOnly = true)
  public void requireWrite(long accountId) {
    AccountMemberEntity member = requireActiveMember(accountId);
    if (!AccountRoleCapabilities.canWrite(member.getRole())) {
      throw SecurityException.accessDeniedForAccount(accountId);
    }
  }

  @Transactional(readOnly = true)
  public void requireManageMembers(long accountId) {
    AccountMemberEntity member = requireActiveMember(accountId);
    if (!AccountRoleCapabilities.canManageMembers(member.getRole())) {
      throw SecurityException.accessDeniedForAccount(accountId);
    }
  }

  @Transactional(readOnly = true)
  public Optional<AccountMemberEntity> findActiveMember(long accountId) {
    long profileId = domainUserContext.requireProfileId();
    return accountMemberRepository.findByAccount_IdAndUser_IdAndStatus(
        accountId,
        profileId,
        AccountMemberStatus.ACTIVE
    );
  }

  @Transactional(readOnly = true)
  public AccountMemberEntity requireActiveMember(long accountId) {
    return findActiveMember(accountId)
        .orElseThrow(() -> SecurityException.accessDeniedForAccount(accountId));
  }

  @Transactional(readOnly = true)
  public List<AccountMemberEntity> findAllMembersByAccount(long accountId) {
    requireManageMembers(accountId);
    return accountMemberRepository.findAllByAccount_Id(accountId);
  }

  @Transactional(readOnly = true)
  public List<AccountMemberEntity> findAllMembershipsByCurrentProfile() {
    long profileId = domainUserContext.requireProfileId();
    return accountMemberRepository.findAllByUser_Id(profileId);
  }
}
