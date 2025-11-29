package io.datapulse.core.service;

import static io.datapulse.core.tx.TransactionCallbacks.afterRollback;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_ACCOUNT_IMMUTABLE;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_ALREADY_EXISTS;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_BY_ID_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_ID_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.DATA_CORRUPTED_ACCOUNT_MISSING;
import static io.datapulse.domain.MessageCodes.DTO_REQUIRED;
import static io.datapulse.domain.MessageCodes.ID_REQUIRED;

import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.AccountConnectionRepository;
import io.datapulse.core.vault.MarketplaceCredentialsVaultService;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
  private final AccountConnectionRepository repository;
  private final AccountService accountService;
  private final AccountConnectionApplier accountConnectionApplier;
  private final MarketplaceCredentialsVaultService vaultService;

  public AccountConnectionService(
      MapperFacade mapperFacade,
      AccountConnectionRepository repository,
      AccountService accountService,
      AccountConnectionApplier accountConnectionApplier,
      MarketplaceCredentialsVaultService vaultService) {
    this.mapperFacade = mapperFacade;
    this.repository = repository;
    this.accountService = accountService;
    this.accountConnectionApplier = accountConnectionApplier;
    this.vaultService = vaultService;
  }

  private void applyCredentialsToVault(AccountConnectionDto dto) {
    Long accountId = dto.getAccountId();
    MarketplaceType marketplace = dto.getMarketplace();
    MarketplaceCredentials credentials = dto.getCredentials();

    if (accountId == null || marketplace == null || credentials == null) {
      return;
    }

    vaultService.saveCredentials(accountId, marketplace, credentials);

    afterRollback(() -> vaultService.deleteCredentials(accountId, marketplace));
  }

  @Override
  public AccountConnectionDto save(
      @Valid @NotNull(message = DTO_REQUIRED) AccountConnectionDto dto) {
    applyCredentialsToVault(dto);
    return super.save(dto);
  }

  @Override
  public AccountConnectionDto update(
      @Valid @NotNull(message = DTO_REQUIRED) AccountConnectionDto dto) {
    applyCredentialsToVault(dto);
    return super.update(dto);
  }

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected JpaRepository<AccountConnectionEntity, Long> repository() {
    return repository;
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
    validateAccountExists(dto.getAccountId());
    if (repository.existsByAccount_IdAndMarketplace(dto.getAccountId(), dto.getMarketplace())) {
      throw new BadRequestException(
          ACCOUNT_CONNECTION_ALREADY_EXISTS,
          dto.getAccountId(),
          dto.getMarketplace());
    }
  }

  @Override
  protected void validateOnUpdate(
      Long id,
      AccountConnectionDto dto,
      AccountConnectionEntity existing) {
    var currentMarketplace = existing.getMarketplace();
    var newMarketplace = dto.getMarketplace();

    if (newMarketplace != null && newMarketplace != currentMarketplace) {
      if (repository.existsByAccount_IdAndMarketplaceAndIdNot(
          existing.getAccount().getId(), newMarketplace, id)) {
        throw new BadRequestException(
            ACCOUNT_CONNECTION_ALREADY_EXISTS,
            existing.getAccount().getId(),
            newMarketplace
        );
      }
    }
  }

  @Override
  protected AccountConnectionEntity beforeSave(AccountConnectionEntity entity) {
    var now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  @Override
  protected AccountConnectionEntity beforeUpdate(AccountConnectionEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return entity;
  }

  @Transactional(readOnly = true)
  public AccountConnectionDto getByAccountIdAndMarketplaceType(
      @NotNull(message = ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType
  ) {
    return repository.findByAccount_IdAndMarketplaceAndActiveTrue(accountId, marketplaceType)
        .map(accountConnection -> mapper().to(accountConnection, AccountConnectionDto.class))
        .orElseThrow(() ->
            new NotFoundException(
                ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND,
                accountId,
                marketplaceType));
  }

  @Transactional(readOnly = true)
  public List<MarketplaceType> getActiveMarketplacesByAccountId(
      @NotNull(message = ACCOUNT_ID_REQUIRED) Long accountId
  ) {
    validateAccountExists(accountId);
    return repository.findAllByAccount_IdAndActiveTrue(accountId)
        .stream()
        .map(AccountConnectionEntity::getMarketplace)
        .distinct()
        .toList();
  }

  @Override
  protected AccountConnectionEntity merge(
      AccountConnectionEntity target,
      AccountConnectionDto source
  ) {
    Long existingAccountId = Optional.ofNullable(target.getAccount())
        .map(AccountEntity::getId)
        .orElseThrow(() -> new AppException(DATA_CORRUPTED_ACCOUNT_MISSING));

    if (source.getAccountId() != null && !source.getAccountId().equals(existingAccountId)) {
      throw new AppException(ACCOUNT_CONNECTION_ACCOUNT_IMMUTABLE);
    }

    accountConnectionApplier.applyUpdateFromDto(source, target);
    return target;
  }

  @Override
  @Transactional
  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException(ACCOUNT_CONNECTION_BY_ID_NOT_FOUND, id);
    }
    repository.deleteById(id);
  }

  private void validateAccountExists(Long accountId) {
    if (accountId == null) {
      throw new BadRequestException(ACCOUNT_ID_REQUIRED);
    }
    if (!accountService.exists(accountId)) {
      throw new NotFoundException(ACCOUNT_NOT_FOUND, accountId);
    }
  }

  @Mapper(
      componentModel = "spring",
      config = BaseMapperConfig.class
  )
  public interface AccountConnectionApplier {

    @Mapping(target = "account", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void applyUpdateFromDto(
        AccountConnectionDto dto,
        @MappingTarget AccountConnectionEntity entity);
  }
}
