package io.datapulse.core.service.account;

import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.account.AccountConnectionRepository;
import io.datapulse.core.service.AbstractIngestApiService;
import io.datapulse.core.service.vault.VaultSyncOutboxService;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.account.AccountConnectionDto;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.request.account.AccountConnectionCreateRequest;
import io.datapulse.domain.request.account.AccountConnectionUpdateRequest;
import io.datapulse.domain.response.account.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AccountConnectionService extends AbstractIngestApiService<
    AccountConnectionCreateRequest,
    AccountConnectionUpdateRequest,
    AccountConnectionResponse,
    AccountConnectionDto,
    AccountConnectionEntity> {

  private final MapperFacade mapperFacade;
  private final AccountConnectionRepository accountConnectionRepository;
  private final AccountService accountService;
  private final AccountConnectionApplier accountConnectionApplier;
  private final VaultSyncOutboxService vaultSyncOutboxService;

  public AccountConnectionService(
      MapperFacade mapperFacade,
      AccountConnectionRepository accountConnectionRepository,
      AccountService accountService,
      AccountConnectionApplier accountConnectionApplier,
      VaultSyncOutboxService vaultSyncOutboxService
  ) {
    this.mapperFacade = mapperFacade;
    this.accountConnectionRepository = accountConnectionRepository;
    this.accountService = accountService;
    this.accountConnectionApplier = accountConnectionApplier;
    this.vaultSyncOutboxService = vaultSyncOutboxService;
  }

  @Override
  @Transactional
  public AccountConnectionDto save(
      @Valid @NotNull(message = ValidationKeys.DTO_REQUIRED) AccountConnectionDto dto
  ) {
    AccountConnectionDto saved = super.save(dto);

    MarketplaceCredentials credentials = dto.getCredentials();
    if (credentials != null) {
      ensureVaultOutboxPresent(saved, credentials);
    }

    return saved;
  }

  @Override
  @Transactional
  public AccountConnectionDto update(
      @Valid @NotNull(message = ValidationKeys.DTO_REQUIRED) AccountConnectionDto dto
  ) {
    AccountConnectionDto updated = super.update(dto);

    MarketplaceCredentials credentials = dto.getCredentials();
    if (credentials != null) {
      ensureVaultOutboxPresent(updated, credentials);
    }

    return updated;
  }

  @Override
  @Transactional
  public void delete(@NotNull(message = ValidationKeys.ID_REQUIRED) Long id) {
    AccountConnectionEntity existing = accountConnectionRepository.findById(id)
        .orElseThrow(
            () -> new NotFoundException(MessageCodes.ACCOUNT_CONNECTION_BY_ID_NOT_FOUND, id));

    accountConnectionRepository.delete(existing);

    Long accountId = Optional.ofNullable(existing.getAccount())
        .map(AccountEntity::getId)
        .orElse(null);

    MarketplaceType marketplace = existing.getMarketplace();

    if (accountId != null && marketplace != null) {
      vaultSyncOutboxService.ensureAbsent(accountId, marketplace);
    }
  }

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected JpaRepository<AccountConnectionEntity, Long> repository() {
    return accountConnectionRepository;
  }

  @Override
  protected Class<AccountConnectionDto> dtoType() {
    return AccountConnectionDto.class;
  }

  @Override
  protected Class<AccountConnectionEntity> entityType() {
    return AccountConnectionEntity.class;
  }

  @Override
  protected Class<AccountConnectionResponse> responseType() {
    return AccountConnectionResponse.class;
  }

  @Override
  protected void validateOnCreate(AccountConnectionDto dto) {
    Long accountId = dto.getAccountId();
    validateAccountExists(accountId);

    MarketplaceType marketplace = dto.getMarketplace();
    if (marketplace == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED);
    }

    if (accountConnectionRepository.existsByAccount_IdAndMarketplace(accountId, marketplace)) {
      throw new BadRequestException(
          MessageCodes.ACCOUNT_CONNECTION_ALREADY_EXISTS,
          accountId,
          marketplace
      );
    }
  }

  @Override
  protected void validateOnUpdate(
      Long id,
      AccountConnectionDto dto,
      AccountConnectionEntity existing
  ) {
    MarketplaceType requestedMarketplace = dto.getMarketplace();
    MarketplaceType currentMarketplace = existing.getMarketplace();

    if (requestedMarketplace == null || requestedMarketplace == currentMarketplace) {
      return;
    }

    AccountEntity account = existing.getAccount();
    if (account == null || account.getId() == null) {
      throw new AppException(MessageCodes.DATA_CORRUPTED_ACCOUNT_MISSING);
    }

    Long accountId = account.getId();

    if (accountConnectionRepository.existsByAccount_IdAndMarketplaceAndIdNot(
        accountId, requestedMarketplace, id
    )) {
      throw new BadRequestException(
          MessageCodes.ACCOUNT_CONNECTION_ALREADY_EXISTS,
          accountId,
          requestedMarketplace
      );
    }
  }

  @Override
  protected AccountConnectionEntity beforeSave(AccountConnectionEntity entity) {
    OffsetDateTime now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  @Override
  protected AccountConnectionEntity beforeUpdate(AccountConnectionEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return entity;
  }

  @Override
  protected AccountConnectionEntity merge(AccountConnectionEntity target,
      AccountConnectionDto source) {
    Long existingAccountId = Optional.ofNullable(target.getAccount())
        .map(AccountEntity::getId)
        .orElseThrow(() -> new AppException(MessageCodes.DATA_CORRUPTED_ACCOUNT_MISSING));

    Long requestedAccountId = source.getAccountId();
    if (requestedAccountId != null && !requestedAccountId.equals(existingAccountId)) {
      throw new AppException(MessageCodes.ACCOUNT_CONNECTION_ACCOUNT_IMMUTABLE);
    }

    accountConnectionApplier.applyUpdateFromDto(source, target);
    return target;
  }

  public void assertActiveConnectionExists(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType
  ) {
    if (!accountConnectionRepository.existsByAccount_IdAndMarketplaceAndActiveTrue(
        accountId,
        marketplaceType)) {
      throw new NotFoundException(
          MessageCodes.ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND,
          accountId,
          marketplaceType
      );
    }
  }

  public Set<MarketplaceType> getActiveMarketplacesByAccountId(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId
  ) {
    return accountConnectionRepository.findAllByAccount_IdAndActiveTrue(accountId)
        .stream()
        .map(AccountConnectionEntity::getMarketplace)
        .collect(Collectors.toSet());
  }

  public Optional<AccountConnectionDto> getByAccountAndMarketplace(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType
  ) {
    return accountConnectionRepository.findByAccount_IdAndMarketplace(accountId, marketplaceType)
        .map(this::toDto);
  }

  private AccountConnectionDto toDto(AccountConnectionEntity entity) {
    return mapperFacade.to(entity, AccountConnectionDto.class);
  }

  private void ensureVaultOutboxPresent(
      AccountConnectionDto dto,
      MarketplaceCredentials credentials) {
    VaultSyncKey key = resolveVaultSyncKey(dto);
    vaultSyncOutboxService.ensurePresent(key.accountId(), key.marketplace(), credentials);
  }

  private VaultSyncKey resolveVaultSyncKey(AccountConnectionDto dto) {
    Long accountId = dto.getAccountId();
    MarketplaceType marketplace = dto.getMarketplace();

    if (accountId != null && marketplace != null) {
      return new VaultSyncKey(accountId, marketplace);
    }

    Long connectionId = dto.getId();
    if (connectionId == null) {
      throw new BadRequestException(MessageCodes.ID_REQUIRED);
    }

    AccountConnectionEntity existing = accountConnectionRepository.findById(connectionId)
        .orElseThrow(() -> new NotFoundException(MessageCodes.ACCOUNT_CONNECTION_BY_ID_NOT_FOUND,
            connectionId));

    Long resolvedAccountId = Optional.ofNullable(existing.getAccount())
        .map(AccountEntity::getId)
        .orElseThrow(() -> new AppException(MessageCodes.DATA_CORRUPTED_ACCOUNT_MISSING));

    MarketplaceType resolvedMarketplace = existing.getMarketplace();
    if (resolvedMarketplace == null) {
      throw new AppException(MessageCodes.DATA_CORRUPTED_MARKETPLACE_MISSING);
    }

    return new VaultSyncKey(resolvedAccountId, resolvedMarketplace);
  }

  private record VaultSyncKey(long accountId, MarketplaceType marketplace) {

  }

  private void validateAccountExists(Long accountId) {
    if (accountId == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_ID_REQUIRED);
    }
    if (!accountService.exists(accountId)) {
      throw new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, accountId);
    }
  }

  @Mapper(componentModel = "spring", config = BaseMapperConfig.class)
  public interface AccountConnectionApplier {

    @Mapping(target = "account", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void applyUpdateFromDto(
        AccountConnectionDto dto,
        @MappingTarget AccountConnectionEntity entity
    );
  }
}
