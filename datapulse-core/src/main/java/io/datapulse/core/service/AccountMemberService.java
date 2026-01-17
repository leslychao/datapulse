package io.datapulse.core.service;

import io.datapulse.core.entity.AccountMemberEntity;
import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.AccountMemberRepository;
import io.datapulse.core.repository.account.AccountRepository;
import io.datapulse.core.repository.userprofile.UserProfileRepository;
import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import io.datapulse.domain.request.AccountMemberCreateRequest;
import io.datapulse.domain.request.AccountMemberUpdateRequest;
import io.datapulse.domain.response.AccountMemberResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountMemberService {

  private final AccountMemberRepository accountMemberRepository;
  private final AccountRepository accountRepository;
  private final UserProfileRepository userProfileRepository;
  private final MapperFacade mapperFacade;

  @Transactional
  public void ensureOwnerMembership(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_MEMBER_CURRENT_USER_ID_REQUIRED) Long currentUserId
  ) {
    accountMemberRepository.findByAccount_IdAndUser_Id(accountId, currentUserId)
        .ifPresentOrElse(
            this::promoteToActiveOwner,
            () -> createOwnerMembershipIdempotent(accountId, currentUserId)
        );
  }

  private void promoteToActiveOwner(AccountMemberEntity existing) {
    existing.setRole(AccountMemberRole.OWNER);
    existing.setStatus(AccountMemberStatus.ACTIVE);
    accountMemberRepository.save(existing);
  }

  private void createOwnerMembershipIdempotent(Long accountId, Long currentUserId) {
    try {
      AccountEntity account = requireAccount(accountId);
      UserProfileEntity user = requireUser(currentUserId);

      AccountMemberEntity entity = new AccountMemberEntity();
      entity.setAccount(account);
      entity.setUser(user);
      entity.setRole(AccountMemberRole.OWNER);
      entity.setStatus(AccountMemberStatus.ACTIVE);

      accountMemberRepository.save(entity);
    } catch (DataIntegrityViolationException race) {
      AccountMemberEntity existing = accountMemberRepository
          .findByAccount_IdAndUser_Id(accountId, currentUserId)
          .orElseThrow(() -> new NotFoundException(
              MessageCodes.ACCOUNT_MEMBER_BY_ACCOUNT_USER_NOT_FOUND,
              currentUserId,
              accountId
          ));

      promoteToActiveOwner(existing);
    }
  }

  @Transactional(readOnly = true)
  public List<AccountMemberResponse> getAllByAccountId(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId
  ) {
    requireAccountExists(accountId);

    return accountMemberRepository.findAllByAccount_IdOrderByIdAsc(accountId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public AccountMemberResponse createMember(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_MEMBER_CURRENT_USER_ID_REQUIRED) Long currentUserId,
      @Valid @NotNull(message = ValidationKeys.REQUEST_REQUIRED) AccountMemberCreateRequest request
  ) {
    AccountEntity account = requireAccount(accountId);
    UserProfileEntity user = requireUser(currentUserId);

    AccountMemberEntity entity = new AccountMemberEntity();
    entity.setAccount(account);
    entity.setUser(user);
    entity.setRole(request.role());
    entity.setStatus(request.status());

    try {
      return toResponse(accountMemberRepository.save(entity));
    } catch (DataIntegrityViolationException ex) {
      if (isActiveOwner(request.role(), request.status())) {
        throw new BadRequestException(MessageCodes.ACCOUNT_MEMBER_SINGLE_OWNER_ONLY, accountId);
      }
      throw new BadRequestException(
          MessageCodes.ACCOUNT_MEMBER_ALREADY_EXISTS,
          accountId,
          currentUserId
      );
    }
  }

  @Transactional
  public AccountMemberResponse updateMember(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ID_REQUIRED) Long memberId,
      @Valid @NotNull(message = ValidationKeys.REQUEST_REQUIRED) AccountMemberUpdateRequest request
  ) {
    AccountMemberEntity existing = requireMember(accountId, memberId);

    assertNotDemotingLastActiveOwner(accountId, existing, request.role(), request.status());

    existing.setRole(request.role());
    existing.setStatus(request.status());

    try {
      return toResponse(accountMemberRepository.save(existing));
    } catch (DataIntegrityViolationException ex) {
      if (isActiveOwner(request.role(), request.status())) {
        throw new BadRequestException(MessageCodes.ACCOUNT_MEMBER_SINGLE_OWNER_ONLY, accountId);
      }
      throw ex;
    }
  }

  private static boolean isActiveOwner(AccountMemberRole role, AccountMemberStatus status) {
    return role == AccountMemberRole.OWNER && status == AccountMemberStatus.ACTIVE;
  }

  @Transactional
  public void deleteMember(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ID_REQUIRED) Long memberId
  ) {
    AccountMemberEntity existing = requireMember(accountId, memberId);

    assertNotLastActiveOwnerDelete(accountId, existing);

    accountMemberRepository.delete(existing);
  }

  private void assertNotLastActiveOwnerDelete(Long accountId, AccountMemberEntity existing) {
    if (!isActiveOwner(existing.getRole(), existing.getStatus())) {
      return;
    }

    List<AccountMemberEntity> owners =
        accountMemberRepository.findActiveOwners(
            accountId,
            AccountMemberRole.OWNER,
            AccountMemberStatus.ACTIVE
        );

    if (owners.size() <= 1) {
      throw new BadRequestException(
          MessageCodes.ACCOUNT_MEMBER_LAST_OWNER_FORBIDDEN,
          accountId
      );
    }
  }

  private void assertNotDemotingLastActiveOwner(
      Long accountId,
      AccountMemberEntity existing,
      AccountMemberRole newRole,
      AccountMemberStatus newStatus
  ) {
    if (!isRemovingActiveOwner(existing, newRole, newStatus)) {
      return;
    }

    List<AccountMemberEntity> owners =
        accountMemberRepository.findActiveOwners(
            accountId,
            AccountMemberRole.OWNER,
            AccountMemberStatus.ACTIVE
        );

    if (owners.size() <= 1) {
      throw new BadRequestException(
          MessageCodes.ACCOUNT_MEMBER_LAST_OWNER_FORBIDDEN,
          accountId
      );
    }
  }

  private static boolean isRemovingActiveOwner(
      AccountMemberEntity existing,
      AccountMemberRole newRole,
      AccountMemberStatus newStatus
  ) {
    if (!isActiveOwner(existing.getRole(), existing.getStatus())) {
      return false;
    }

    boolean roleDowngraded = newRole != null && newRole != AccountMemberRole.OWNER;
    boolean statusDeactivated = newStatus != null && newStatus != AccountMemberStatus.ACTIVE;

    return roleDowngraded || statusDeactivated;
  }

  private AccountMemberEntity requireMember(Long accountId, Long memberId) {
    requireAccountExists(accountId);

    return accountMemberRepository.findByIdAndAccount_Id(memberId, accountId)
        .orElseThrow(() ->
            new NotFoundException(MessageCodes.ACCOUNT_MEMBER_NOT_FOUND, memberId));
  }

  private AccountEntity requireAccount(Long accountId) {
    return accountRepository.findById(accountId)
        .orElseThrow(() ->
            new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, accountId));
  }

  private void requireAccountExists(Long accountId) {
    if (!accountRepository.existsById(accountId)) {
      throw new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, accountId);
    }
  }

  private UserProfileEntity requireUser(Long userId) {
    if (userId == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_MEMBER_CURRENT_USER_ID_REQUIRED);
    }

    return userProfileRepository.findById(userId)
        .orElseThrow(() ->
            new NotFoundException(MessageCodes.USER_PROFILE_BY_ID_NOT_FOUND, userId));
  }

  private AccountMemberResponse toResponse(AccountMemberEntity entity) {
    return mapperFacade.to(entity, AccountMemberResponse.class);
  }
}
