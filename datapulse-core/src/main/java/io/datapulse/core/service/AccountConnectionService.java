package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.*;

import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.AccountConnectionRepository;
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

  private final AccountService accountService;
  private final AccountConnectionRepository repository;

  public AccountConnectionService(
      MapperFacade mapperFacade,
      AccountService accountService,
      AccountConnectionRepository repository
  ) {
    super(mapperFacade);
    this.accountService = accountService;
    this.repository = repository;
  }

  @Override protected Class<AccountConnectionDto> dtoType() { return AccountConnectionDto.class; }
  @Override protected Class<AccountConnectionEntity> entityType() { return AccountConnectionEntity.class; }
  @Override protected Class<AccountConnectionResponse> responseType() { return AccountConnectionResponse.class; }
  @Override protected JpaRepository<AccountConnectionEntity, Long> repository() { return repository; }

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

  @Override
  protected AccountConnectionEntity merge(@NotNull AccountConnectionEntity target,
      @Valid @NotNull AccountConnectionDto source) {
    Long currentAccountId = target.getAccount() != null ? target.getAccount().getId() : null;

    // Иммутабельность привязки к аккаунту
    if (currentAccountId != null && source.getAccountId() != null && !source.getAccountId().equals(currentAccountId)) {
      throw new AppException(ACCOUNT_CONNECTION_ID_IMMUTABLE);
    }
    // Ленивая инициализация линка на аккаунт при первом сохранении
    if (currentAccountId == null && source.getAccountId() != null) {
      var account = new AccountEntity();
      account.setId(source.getAccountId());
      target.setAccount(account);
    }

    // Единый путь мерджа через MapperFacade
    mapper().merge(source, target);
    return target;
  }

  // ---------- Доменные операции ----------

  @Transactional(readOnly = true)
  public AccountConnectionDto getByAccountIdAndMarketplaceType(
      @NotNull(message = ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType) {
    return repository.findByAccount_IdAndMarketplaceAndActiveTrue(accountId, marketplaceType)
        .map(e -> mapper().to(e, AccountConnectionDto.class))
        .orElseThrow(() -> new NotFoundException(ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND, accountId, marketplaceType));
  }

  @Transactional
  public AccountConnectionResponse create(
      @NotNull(message = ACCOUNT_CONNECTION_CREATE_REQUEST_REQUIRED) @Valid AccountConnectionCreateRequest request) {
    validateAccountExists(request.accountId());
    if (repository.existsByAccount_IdAndMarketplace(request.accountId(), request.marketplace())) {
      throw new BadRequestException(ACCOUNT_CONNECTION_ALREADY_EXISTS, request.accountId(), request.marketplace());
    }
    // CreateReq -> DTO -> save -> Response (всё через MapperFacade)
    var dto   = mapper().to(request, dtoType());
    var saved = save(dto);
    return mapper().to(saved, responseType());
  }

  @Transactional
  public AccountConnectionResponse update(
      @NotNull(message = ID_REQUIRED) Long id,
      @NotNull(message = ACCOUNT_CONNECTION_UPDATE_REQUEST_REQUIRED) @Valid AccountConnectionUpdateRequest request) {

    var current = get(id).orElseThrow(() -> new NotFoundException(ACCOUNT_CONNECTION_BY_ID_NOT_FOUND, id));

    // Проверка уникальности (аккаунт + marketplace) при смене marketplace
    if (request.marketplace() != null && request.marketplace() != current.getMarketplace()) {
      if (repository.existsByAccount_IdAndMarketplaceAndIdNot(current.getAccountId(), request.marketplace(), id)) {
        throw new BadRequestException(ACCOUNT_CONNECTION_ALREADY_EXISTS, current.getAccountId(), request.marketplace());
      }
    }

    // UpdateReq -> DTO (patch) -> merge(patch, current) -> update -> Response
    var patch = mapper().to(request, dtoType());
    merge(mapper().to(current, entityType()), patch); // обеспечим общие инварианты из merge(...)
    mapper().merge(patch, current);
    var updated = update(current);
    return mapper().to(updated, responseType());
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
}
