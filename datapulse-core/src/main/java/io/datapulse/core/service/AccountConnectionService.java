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
import io.datapulse.core.converter.AccountConnectionMapper;
import io.datapulse.core.converter.BeanConverter;
import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.repository.AccountConnectionRepository;
import io.datapulse.core.service.crypto.CryptoService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class AccountConnectionService
    extends AbstractCrudService<AccountConnectionDto, AccountConnectionEntity> {

  private final AccountService accountService;
  private final AccountConnectionRepository connectionRepository;
  private final AccountConnectionMapper mapper;
  private final ObjectMapper objectMapper;
  private final CryptoService cryptoService;

  @Override
  protected BeanConverter<AccountConnectionDto, AccountConnectionEntity> getConverter() {
    return mapper;
  }

  @Override
  protected JpaRepository<AccountConnectionEntity, Long> getRepository() {
    return connectionRepository;
  }

  @Override
  protected AccountConnectionEntity entityPreSaveAction(AccountConnectionEntity entity) {
    var now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  @Override
  protected AccountConnectionEntity entityPreUpdateAction(AccountConnectionEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return entity;
  }

  @Transactional(readOnly = true)
  public AccountConnectionDto getByAccountIdAndMarketplaceType(
      @NotNull(message = ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType) {

    return connectionRepository
        .findByAccount_IdAndMarketplaceAndActiveTrue(accountId, marketplaceType)
        .map(mapper::mapToDto)
        .orElseThrow(
            () -> new NotFoundException(ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND,
                accountId, marketplaceType));
  }

  @Transactional
  public AccountConnectionResponse create(
      @NotNull(message = ACCOUNT_CONNECTION_CREATE_REQUEST_REQUIRED)
      @Valid AccountConnectionCreateRequest request) {

    validateAccountExists(request.accountId());

    if (connectionRepository.existsByAccount_IdAndMarketplace(request.accountId(),
        request.marketplaceType())) {
      throw new BadRequestException(
          ACCOUNT_CONNECTION_ALREADY_EXISTS,
          request.accountId(),
          request.marketplaceType());
    }

    var dto = mapper.fromCreateRequest(request, cryptoService, objectMapper);
    var saved = save(dto);
    return mapper.toResponse(saved);
  }

  @Transactional
  public AccountConnectionResponse update(
      @NotNull(message = ID_REQUIRED) Long id,
      @NotNull(message = ACCOUNT_CONNECTION_UPDATE_REQUEST_REQUIRED) @Valid AccountConnectionUpdateRequest request) {

    AccountConnectionDto current = get(id)
        .orElseThrow(() -> new NotFoundException(ACCOUNT_CONNECTION_BY_ID_NOT_FOUND, id));

    if (request.marketplaceType() != null
        && request.marketplaceType() != current.getMarketplace()) {
      if (connectionRepository.existsByAccount_IdAndMarketplaceAndIdNot(
          current.getAccountId(),
          request.marketplaceType(),
          id)) {
        throw new BadRequestException(
            ACCOUNT_CONNECTION_ALREADY_EXISTS,
            current.getAccountId(),
            request.marketplaceType());
      }
    }

    mapper.applyUpdate(request, current, cryptoService, objectMapper);
    var updated = update(current);
    return mapper.toResponse(updated);
  }

  @Override
  @Transactional
  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    if (!connectionRepository.existsById(id)) {
      throw new NotFoundException(ACCOUNT_CONNECTION_BY_ID_NOT_FOUND, id);
    }
    connectionRepository.deleteById(id);
  }

  @Override
  protected AccountConnectionEntity updateEntityWithDto(
      AccountConnectionEntity target,
      AccountConnectionDto dto) {
    Long currentAccountId = target.getAccount() != null ? target.getAccount().getId() : null;

    if (currentAccountId != null && dto.getAccountId() != null && !dto.getAccountId()
        .equals(currentAccountId)) {
      throw new AppException(ACCOUNT_CONNECTION_ID_IMMUTABLE);
    }

    if (currentAccountId == null && dto.getAccountId() != null) {
      var account = new AccountEntity();
      account.setId(dto.getAccountId());
      target.setAccount(account);
    }

    mapper.applyUpdateFromDto(dto, target);
    return target;
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
