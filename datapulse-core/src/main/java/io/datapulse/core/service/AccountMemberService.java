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
import io.datapulse.domain.dto.AccountMemberDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import io.datapulse.domain.request.AccountMemberCreateRequest;
import io.datapulse.domain.request.AccountMemberUpdateRequest;
import io.datapulse.domain.response.AccountMemberResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AccountMemberService extends AbstractIngestApiService<
    AccountMemberCreateRequest,
    AccountMemberUpdateRequest,
    AccountMemberResponse,
    AccountMemberDto,
    AccountMemberEntity> {

  private final AccountMemberRepository accountMemberRepository;
  private final AccountRepository accountRepository;
  private final UserProfileRepository userProfileRepository;
  private final MapperFacade mapperFacade;

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected JpaRepository<AccountMemberEntity, Long> repository() {
    return accountMemberRepository;
  }

  @Override
  protected Class<AccountMemberDto> dtoType() {
    return AccountMemberDto.class;
  }

  @Override
  protected Class<AccountMemberEntity> entityType() {
    return AccountMemberEntity.class;
  }

  @Override
  protected Class<AccountMemberResponse> responseType() {
    return AccountMemberResponse.class;
  }

  @Transactional
  public void ensureOwnerMembership(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_MEMBER_CURRENT_USER_ID_REQUIRED) Long userId
  ) {
    accountMemberRepository.findByAccount_IdAndUser_Id(accountId, userId)
        .ifPresentOrElse(
            existing -> updateToOwnerActive(existing.getId()),
            () -> createOwnerActiveIdempotent(accountId, userId)
        );
  }

  private void updateToOwnerActive(Long memberId) {
    AccountMemberUpdateRequest updateRequest = new AccountMemberUpdateRequest(
        AccountMemberRole.OWNER,
        AccountMemberStatus.ACTIVE
    );
    updateFromRequest(memberId, updateRequest);
  }

  private void createOwnerActiveIdempotent(Long accountId, Long userId) {
    try {
      AccountMemberCreateRequest createRequest = new AccountMemberCreateRequest(
          accountId,
          userId,
          AccountMemberRole.OWNER,
          AccountMemberStatus.ACTIVE
      );
      createFromRequest(createRequest);
    } catch (DataIntegrityViolationException race) {
      AccountMemberEntity existing = accountMemberRepository
          .findByAccount_IdAndUser_Id(accountId, userId)
          .orElseThrow(() -> new NotFoundException(
              MessageCodes.ACCOUNT_MEMBER_BY_ACCOUNT_USER_NOT_FOUND,
              userId,
              accountId
          ));
      updateToOwnerActive(existing.getId());
    }
  }

  @Override
  protected void validateOnCreate(AccountMemberDto dto) {
    if (dto.getAccountId() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_ACCOUNT_ID_REQUIRED);
    }
    if (dto.getUserId() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_USER_ID_REQUIRED);
    }
    if (dto.getRole() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_ROLE_REQUIRED);
    }
    if (dto.getStatus() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_STATUS_REQUIRED);
    }
    if (accountMemberRepository.existsByAccount_IdAndUser_Id(dto.getAccountId(), dto.getUserId())) {
      throw new BadRequestException(
          MessageCodes.ACCOUNT_MEMBER_ALREADY_EXISTS,
          dto.getAccountId(),
          dto.getUserId()
      );
    }
  }

  @Override
  protected void validateOnUpdate(Long id, AccountMemberDto dto, AccountMemberEntity existing) {
    if (dto.getAccountId() != null && !dto.getAccountId().equals(existing.getAccount().getId())) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_ACCOUNT_IMMUTABLE);
    }
    if (dto.getUserId() != null && !dto.getUserId().equals(existing.getUser().getId())) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_USER_IMMUTABLE);
    }
    if (dto.getRole() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_ROLE_REQUIRED);
    }
    if (dto.getStatus() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_STATUS_REQUIRED);
    }
  }

  @Override
  protected AccountMemberEntity beforeSave(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      AccountMemberEntity entity
  ) {
    Long accountId = extractAccountId(entity);
    Long userId = extractUserId(entity);

    AccountEntity account = accountRepository.findById(accountId)
        .orElseThrow(() -> new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, accountId));

    UserProfileEntity user = userProfileRepository.findById(userId)
        .orElseThrow(
            () -> new NotFoundException(MessageCodes.USER_PROFILE_BY_ID_NOT_FOUND, userId));

    entity.setAccount(account);
    entity.setUser(user);
    return entity;
  }

  @Override
  protected AccountMemberEntity merge(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      AccountMemberEntity target,
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      AccountMemberDto source
  ) {
    target.setRole(source.getRole());
    target.setStatus(source.getStatus());
    return target;
  }

  private static Long extractAccountId(AccountMemberEntity entity) {
    if (entity.getAccount() == null || entity.getAccount().getId() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_ACCOUNT_ID_REQUIRED);
    }
    return entity.getAccount().getId();
  }

  private static Long extractUserId(AccountMemberEntity entity) {
    if (entity.getUser() == null || entity.getUser().getId() == null) {
      throw new AppException(MessageCodes.ACCOUNT_MEMBER_USER_ID_REQUIRED);
    }
    return entity.getUser().getId();
  }
}
