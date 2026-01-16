package io.datapulse.core.service.account;

import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.account.AccountRepository;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import io.datapulse.domain.request.account.AccountCreateRequest;
import io.datapulse.domain.request.account.AccountUpdateRequest;
import io.datapulse.domain.response.account.AccountResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository repository;
  private final MapperFacade mapperFacade;

  @Transactional(readOnly = true)
  public Long findActiveAccountIdByNameIgnoreCase(String accountName) {
    String name = normalizeAccountName(accountName);
    if (name == null) {
      return null;
    }

    return repository.findByActiveIsTrueAndNameIgnoreCase(name)
        .map(AccountEntity::getId)
        .orElse(null);
  }

  @Transactional
  public AccountResponse createFromRequest(
      @Valid @NotNull(message = ValidationKeys.REQUEST_REQUIRED) AccountCreateRequest request
  ) {
    String name = normalizeAccountName(request.name());
    if (name == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_NAME_REQUIRED);
    }
    if (repository.existsByNameIgnoreCase(name)) {
      throw new BadRequestException(MessageCodes.ACCOUNT_ALREADY_EXISTS, name);
    }

    AccountEntity entity = new AccountEntity();
    entity.setName(name);
    entity.setActive(request.active() == null || request.active());

    AccountEntity saved = repository.save(entity);
    return mapperFacade.to(saved, AccountResponse.class);
  }

  @Transactional
  public AccountResponse updateFromRequest(
      @NotNull(message = ValidationKeys.ID_REQUIRED) Long id,
      @Valid @NotNull(message = ValidationKeys.REQUEST_REQUIRED) AccountUpdateRequest request
  ) {
    AccountEntity existing = repository.findById(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, id));

    String newName = normalizeAccountName(request.name());
    if (newName == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_NAME_REQUIRED);
    }

    String oldName = normalizeAccountName(existing.getName());
    if (!StringUtils.equalsIgnoreCase(newName, oldName)
        && repository.existsByNameIgnoreCaseAndIdNot(newName, id)) {
      throw new BadRequestException(MessageCodes.ACCOUNT_ALREADY_EXISTS, newName);
    }

    existing.setName(newName);

    if (request.active() != null) {
      existing.setActive(request.active());
    }

    AccountEntity saved = repository.save(existing);
    return mapperFacade.to(saved, AccountResponse.class);
  }

  @Transactional
  public void delete(@NotNull(message = ValidationKeys.ID_REQUIRED) Long id) {
    AccountEntity existing = repository.findById(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, id));
    repository.delete(existing);
  }

  @Transactional(readOnly = true)
  public boolean exists(@NotNull(message = ValidationKeys.ID_REQUIRED) Long id) {
    return repository.existsById(id);
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> getActive() {
    return repository.findAllByActiveIsTrue()
        .stream()
        .map(entity -> mapperFacade.to(entity, AccountResponse.class))
        .toList();
  }

  private static String normalizeAccountName(String name) {
    return StringUtils.trimToNull(name);
  }
}
