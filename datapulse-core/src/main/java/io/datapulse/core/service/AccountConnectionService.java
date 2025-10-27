package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_ID_IMMUTABLE;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ACCOUNT_ID_REQUIRED;

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
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import java.time.OffsetDateTime;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
  protected AccountConnectionEntity entityPreSaveAction(@NonNull AccountConnectionEntity entity) {
    Long accountId = entity.getAccount() != null ? entity.getAccount().getId() : null;
    if (accountId == null) {
      throw new AppException("account.id.required");
    }
    if (connectionRepository.existsByAccount_IdAndMarketplace(accountId, entity.getMarketplace())) {
      throw new AppException("account.connection.already-exists", accountId,
          entity.getMarketplace());
    }
    return entity;
  }

  @Override
  protected BeanConverter<AccountConnectionDto, AccountConnectionEntity> getConverter() {
    return mapper;
  }

  @Override
  protected JpaRepository<AccountConnectionEntity, Long> getRepository() {
    return connectionRepository;
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
        .orElseThrow(() ->
            new NotFoundException(ACCOUNT_CONNECTION_NOT_FOUND, accountId, marketplaceType));
  }

  @Override
  protected AccountConnectionEntity updateEntityWithDto(
      @NonNull AccountConnectionEntity accountConnectionEntity, @NonNull AccountConnectionDto dto) {
    if (dto.getAccountId() != null) {
      Long existingId = accountConnectionEntity.getAccount() == null
          ? null
          : accountConnectionEntity.getAccount().getId();
      if (existingId != null && !existingId.equals(dto.getAccountId())) {
        throw new AppException(ACCOUNT_CONNECTION_ID_IMMUTABLE);
      }
      if (dto.getMarketplace() != null
          && dto.getMarketplace() != accountConnectionEntity.getMarketplace()) {
        boolean conflict = connectionRepository.existsByAccount_IdAndMarketplaceAndIdNot(
            existingId,
            dto.getMarketplace(),
            accountConnectionEntity.getId());
        if (conflict) {
          throw new AppException("account.connection.already-exists", existingId,
              dto.getMarketplace());
        }
      }
      if (existingId == null) {
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setId(dto.getAccountId());
        accountConnectionEntity.setAccount(accountEntity);
      }
    }
    if (dto.getMarketplace() != null) {
      accountConnectionEntity.setMarketplace(dto.getMarketplace());
    }
    if (dto.getCredentialsEncrypted() != null) {
      accountConnectionEntity.setCredentialsEncrypted(dto.getCredentialsEncrypted());
    }
    if (dto.getActive() != null) {
      accountConnectionEntity.setActive(dto.getActive());
    }
    if (dto.getLastSyncAt() != null) {
      accountConnectionEntity.setLastSyncAt(dto.getLastSyncAt());
    }
    if (dto.getLastSyncStatus() != null) {
      accountConnectionEntity.setLastSyncStatus(dto.getLastSyncStatus());
    }
    accountConnectionEntity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return accountConnectionEntity;
  }

  @Transactional
  public AccountConnectionResponse create(@NonNull AccountConnectionCreateRequest request) {
    if (!accountService.exists(request.accountId())) {
      throw new NotFoundException("account.notFound");
    }
    var dto = mapper.fromCreateRequest(request, cryptoService, objectMapper);
    var saved = save(dto);
    return mapper.toResponse(saved);
  }

  @Override
  public void delete(@NonNull Long id) {
    if (!connectionRepository.existsById(id)) {
      throw new NotFoundException("account.connection.not-found", id);
    }
    connectionRepository.deleteById(id);
  }
}
