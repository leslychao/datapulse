package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_ALREADY_EXISTS;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_ID_IMMUTABLE;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ACCOUNT_ID_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_NOT_FOUND;

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
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
  protected AccountConnectionEntity entityPreSaveAction(@NonNull AccountConnectionEntity entity) {
    Long accountId = entity.getAccount() != null ? entity.getAccount().getId() : null;
    validateNewConnection(accountId, entity.getMarketplace());

    var now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  @Override
  protected AccountConnectionEntity entityPreUpdateAction(@NonNull AccountConnectionEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return entity;
  }

  @Transactional(readOnly = true)
  public AccountConnectionDto getByAccountIdAndMarketplaceType(
      Long accountId,
      MarketplaceType marketplaceType) {
    if (accountId == null) {
      throw new BadRequestException(ACCOUNT_ID_REQUIRED);
    }
    return connectionRepository
        .findByAccount_IdAndMarketplaceAndActiveTrue(accountId, marketplaceType)
        .map(mapper::mapToDto)
        .orElseThrow(
            () -> new NotFoundException(ACCOUNT_CONNECTION_NOT_FOUND, accountId, marketplaceType));
  }

  @Transactional
  public AccountConnectionResponse create(@NonNull AccountConnectionCreateRequest request) {
    validateAccountExists(request.accountId());
    validateNewConnection(request.accountId(), request.marketplaceType());

    var dto = mapper.fromCreateRequest(request, cryptoService, objectMapper);
    var saved = save(dto);
    return mapper.toResponse(saved);
  }

  @Transactional
  public AccountConnectionResponse update(
      @NonNull Long id,
      @NonNull AccountConnectionUpdateRequest request) {
    AccountConnectionDto dto = get(id)
        .orElseThrow(() -> new NotFoundException(ACCOUNT_CONNECTION_NOT_FOUND, id));

    validateImmutabilityOnUpdate(dto.getAccountId(), request.accountId());
    validateMarketplaceUniquenessOnUpdate(
        dto.getAccountId(),
        dto.getMarketplace(),
        request.marketplaceType(),
        id);

    mapper.applyUpdate(request, dto, cryptoService, objectMapper);

    return mapper.toResponse(update(dto));
  }

  public void delete(@NonNull Long id) {
    try {
      connectionRepository.deleteById(id);
    } catch (EmptyResultDataAccessException e) {
      throw new NotFoundException(ACCOUNT_CONNECTION_NOT_FOUND, id);
    }
  }

  @Override
  protected AccountConnectionEntity updateEntityWithDto(
      @NonNull AccountConnectionEntity target,
      @NonNull AccountConnectionDto dto) {
    if (target.getAccount() == null && dto.getAccountId() != null) {
      AccountEntity accountEntity = new AccountEntity();
      accountEntity.setId(dto.getAccountId());
      target.setAccount(accountEntity);
    }
    mapper.applyUpdateFromDto(dto, target);
    return target;
  }

  private void validateNewConnection(Long accountId, MarketplaceType marketplace) {
    if (accountId == null) {
      throw new BadRequestException(ACCOUNT_ID_REQUIRED);
    }
    if (marketplace == null) {
      throw new BadRequestException(ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED);
    }
    if (connectionRepository.existsByAccount_IdAndMarketplace(accountId, marketplace)) {
      throw new AppException(ACCOUNT_CONNECTION_ALREADY_EXISTS, accountId, marketplace);
    }
  }

  private void validateImmutabilityOnUpdate(Long currentAccountId, Long requestedAccountId) {
    if (requestedAccountId != null && !Objects.equals(currentAccountId, requestedAccountId)) {
      throw new AppException(ACCOUNT_CONNECTION_ID_IMMUTABLE);
    }
  }

  private void validateMarketplaceUniquenessOnUpdate(
      Long accountId,
      MarketplaceType currentMarketplace,
      MarketplaceType requestedMarketplace,
      Long selfId) {
    if (accountId == null) {
      throw new BadRequestException(ACCOUNT_ID_REQUIRED);
    }
    if (requestedMarketplace != null && requestedMarketplace != currentMarketplace) {
      if (connectionRepository.existsByAccount_IdAndMarketplaceAndIdNot(
          accountId,
          requestedMarketplace,
          selfId)) {
        throw new AppException(ACCOUNT_CONNECTION_ALREADY_EXISTS, accountId, requestedMarketplace);
      }
    }
  }

  private void validateAccountExists(Long accountId) {
    if (!accountService.exists(accountId)) {
      throw new NotFoundException(ACCOUNT_NOT_FOUND, accountId);
    }
  }
}
