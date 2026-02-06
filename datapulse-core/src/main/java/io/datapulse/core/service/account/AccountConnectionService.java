package io.datapulse.core.service.account;

import io.datapulse.core.codec.CredentialsCodec;
import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.account.AccountConnectionRepository;
import io.datapulse.core.repository.account.AccountRepository;
import io.datapulse.core.service.vault.VaultSyncOutboxService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import io.datapulse.domain.request.account.AccountConnectionCreateRequest;
import io.datapulse.domain.request.account.AccountConnectionUpdateRequest;
import io.datapulse.domain.response.account.AccountConnectionResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AccountConnectionService {

  private final MapperFacade mapperFacade;
  private final AccountConnectionRepository accountConnectionRepository;
  private final AccountRepository accountRepository;
  private final VaultSyncOutboxService vaultSyncOutboxService;
  private final CredentialsCodec credentialsCodec;

  @Transactional(readOnly = true)
  public List<AccountConnectionResponse> getAllByAccountId(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId
  ) {
    return accountConnectionRepository.findAllByAccount_Id(accountId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<AccountConnectionResponse> getByAccountAndMarketplace(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType
  ) {
    return accountConnectionRepository.findByAccount_IdAndMarketplace(accountId, marketplaceType)
        .map(this::toResponse);
  }

  @Transactional
  public AccountConnectionResponse create(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.REQUEST_REQUIRED) AccountConnectionCreateRequest request
  ) {
    AccountEntity account = requireAccountEntity(accountId);

    MarketplaceType marketplace = request.marketplace();
    if (marketplace == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED);
    }

    MarketplaceCredentials credentials = request.credentials();
    if (credentials == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED);
    }
    if (!marketplace.supports(credentials)) {
      throw new BadRequestException(MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH);
    }

    if (accountConnectionRepository.existsByAccount_IdAndMarketplace(accountId, marketplace)) {
      throw new BadRequestException(
          MessageCodes.ACCOUNT_CONNECTION_ALREADY_EXISTS,
          accountId,
          marketplace
      );
    }

    AccountConnectionEntity entity = new AccountConnectionEntity();
    entity.setAccount(account);
    entity.setMarketplace(marketplace);
    entity.setActive(true);
    entity.setMaskedCredentials(credentialsCodec.mask(marketplace, credentials));

    AccountConnectionEntity saved;
    try {
      saved = accountConnectionRepository.save(entity);
    } catch (DataIntegrityViolationException violation) {
      throw new BadRequestException(
          MessageCodes.ACCOUNT_CONNECTION_ALREADY_EXISTS,
          accountId,
          marketplace
      );
    }

    vaultSyncOutboxService.ensurePresent(accountId, marketplace, credentials);

    return mapperFacade.to(saved, AccountConnectionResponse.class);
  }

  @Transactional
  public AccountConnectionResponse update(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ID_REQUIRED) Long connectionId,
      @NotNull(message = ValidationKeys.REQUEST_REQUIRED) AccountConnectionUpdateRequest request
  ) {
    AccountConnectionEntity existing = requireConnectionEntity(accountId, connectionId);

    MarketplaceType marketplace = existing.getMarketplace();

    MarketplaceCredentials credentials = request.credentials();
    Boolean requestedActive = request.active();

    if (credentials != null) {
      if (!marketplace.supports(credentials)) {
        throw new BadRequestException(MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH);
      }
      existing.setMaskedCredentials(credentialsCodec.mask(marketplace, credentials));
      existing.setActive(true);
      vaultSyncOutboxService.ensurePresent(accountId, marketplace, credentials);
    } else if (requestedActive != null) {
      if (requestedActive) {
        if (!vaultSyncOutboxService.isPresent(accountId, marketplace)) {
          throw new BadRequestException(MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED);
        }
      }
      existing.setActive(requestedActive);
    }

    AccountConnectionEntity saved = accountConnectionRepository.save(existing);
    return mapperFacade.to(saved, AccountConnectionResponse.class);
  }

  @Transactional
  public void delete(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ID_REQUIRED) Long connectionId
  ) {
    AccountConnectionEntity existing = requireConnectionEntity(accountId, connectionId);

    MarketplaceType marketplace = existing.getMarketplace();

    accountConnectionRepository.delete(existing);
    vaultSyncOutboxService.ensureAbsent(accountId, marketplace);
  }

  @Transactional(readOnly = true)
  public void assertActiveConnectionExists(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED) MarketplaceType marketplaceType
  ) {
    requireAccountEntity(accountId);

    if (!accountConnectionRepository.existsByAccount_IdAndMarketplaceAndActiveTrue(
        accountId,
        marketplaceType
    )) {
      throw new NotFoundException(
          MessageCodes.ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND,
          accountId,
          marketplaceType
      );
    }
  }

  @Transactional(readOnly = true)
  public Set<MarketplaceType> getActiveMarketplacesByAccountId(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId
  ) {
    requireAccountEntity(accountId);

    return accountConnectionRepository.findAllByAccount_IdAndActiveTrue(accountId)
        .stream()
        .map(AccountConnectionEntity::getMarketplace)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private AccountConnectionResponse toResponse(AccountConnectionEntity entity) {
    return mapperFacade.to(entity, AccountConnectionResponse.class);
  }

  private AccountEntity requireAccountEntity(Long accountId) {
    return accountRepository.findById(accountId)
        .orElseThrow(() -> new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, accountId));
  }

  private AccountConnectionEntity requireConnectionEntity(Long accountId, Long connectionId) {
    AccountConnectionEntity existing = accountConnectionRepository
        .findByIdAndAccount_Id(connectionId, accountId)
        .orElseThrow(() -> new NotFoundException(
            MessageCodes.ACCOUNT_CONNECTION_BY_ID_NOT_FOUND,
            connectionId
        ));

    if (existing.getMarketplace() == null) {
      throw new AppException(MessageCodes.DATA_CORRUPTED_MARKETPLACE_MISSING);
    }

    return existing;
  }
}
