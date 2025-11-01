package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_ALREADY_EXISTS;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_BY_ID_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_CREATE_REQUEST_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_ID_IMMUTABLE;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_UPDATE_REQUEST_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_ID_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ID_REQUIRED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.mapper.MapStructCentralMapperConfig;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.AccountConnectionRepository;
import io.datapulse.core.service.crypto.CryptoService;
import io.datapulse.core.util.JsonUtils;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
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
  private final CryptoService cryptoService;
  private final ObjectMapper objectMapper;

  public AccountConnectionService(
      MapperFacade mapperFacade,
      AccountConnectionRepository repository,
      AccountService accountService,
      AccountConnectionApplier accountConnectionApplier,
      CryptoService cryptoService,
      ObjectMapper objectMapper) {
    this.mapperFacade = mapperFacade;
    this.repository = repository;
    this.accountService = accountService;
    this.accountConnectionApplier = accountConnectionApplier;
    this.cryptoService = cryptoService;
    this.objectMapper = objectMapper;
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
  protected Class<AccountConnectionResponse> responseClass() {
    return AccountConnectionResponse.class;
  }

  @Override
  protected AccountConnectionEntity beforeSave(@NotNull AccountConnectionEntity entity) {
    var now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(now);
    }
    entity.setUpdatedAt(now);
    return entity;
  }

  @Override
  protected AccountConnectionEntity beforeUpdate(@NotNull AccountConnectionEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return entity;
  }

  @Transactional(readOnly = true)
  public AccountConnectionDto getByAccountIdAndMarketplaceType(
      @NotNull(message = ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType
  ) {
    return ((AccountConnectionRepository) repository()).findByAccount_IdAndMarketplaceAndActiveTrue(
            accountId, marketplaceType)
        .map(e -> mapper().to(e, AccountConnectionDto.class))
        .orElseThrow(() ->
            new NotFoundException(ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND, accountId,
                marketplaceType));
  }

  @Transactional
  public AccountConnectionResponse create(
      @NotNull(message = ACCOUNT_CONNECTION_CREATE_REQUEST_REQUIRED) @Valid AccountConnectionCreateRequest request
  ) {
    validateAccountExists(request.accountId());
    if (repository.existsByAccount_IdAndMarketplace(request.accountId(), request.marketplace())) {
      throw new BadRequestException(
          ACCOUNT_CONNECTION_ALREADY_EXISTS,
          request.accountId(),
          request.marketplace());
    }
    var dto = mapperFacade.to(request, dtoType());
    var saved = save(dto);
    return mapper().to(saved, responseClass());
  }

  @Transactional
  public AccountConnectionResponse update(
      @NotNull(message = ID_REQUIRED) Long id,
      @NotNull(message = ACCOUNT_CONNECTION_UPDATE_REQUEST_REQUIRED) @Valid AccountConnectionUpdateRequest request
  ) {
    var current = get(id).orElseThrow(
        () -> new NotFoundException(ACCOUNT_CONNECTION_BY_ID_NOT_FOUND, id));

    if (request.marketplace() != null && request.marketplace() != current.getMarketplace()) {
      if (repository.existsByAccount_IdAndMarketplaceAndIdNot(
          current.getAccountId(),
          request.marketplace(),
          id)) {
        throw new BadRequestException(
            ACCOUNT_CONNECTION_ALREADY_EXISTS,
            current.getAccountId(),
            request.marketplace());
      }
    }
    accountConnectionApplier.applyUpdate(request, current, cryptoService, objectMapper);
    var updated = update(current);
    return mapper().to(updated, responseClass());
  }

  @Override
  protected AccountConnectionEntity merge(
      @NotNull AccountConnectionEntity target,
      @Valid @NotNull AccountConnectionDto source
  ) {
    Long currentAccountId = target.getAccount() != null ? target.getAccount().getId() : null;

    if (currentAccountId != null
        && source.getAccountId() != null
        && !source.getAccountId().equals(currentAccountId)) {
      throw new AppException(ACCOUNT_CONNECTION_ID_IMMUTABLE);
    }
    if (currentAccountId == null && source.getAccountId() != null) {
      var account = new AccountEntity();
      account.setId(source.getAccountId());
      target.setAccount(account);
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
      config = MapStructCentralMapperConfig.class
  )
  public interface AccountConnectionApplier {

    default void applyUpdate(
        AccountConnectionUpdateRequest request,
        @MappingTarget AccountConnectionDto dto,
        CryptoService cryptoService,
        ObjectMapper objectMapper) {
      if (request.marketplace() != null) {
        dto.setMarketplace(request.marketplace());
      }
      if (request.active() != null) {
        dto.setActive(request.active());
      }
      if (request.credentials() != null) {
        dto.setCredentialsEncrypted(
            cryptoService.encrypt(JsonUtils.writeJson(request.credentials(), objectMapper)));
      }
    }

    @Mapping(target = "account", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void applyUpdateFromDto(
        AccountConnectionDto dto,
        @MappingTarget AccountConnectionEntity entity);
  }
}
