package io.datapulse.core.service;

import io.datapulse.core.converter.AccountMapper;
import io.datapulse.core.converter.BeanConverter;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.repository.AccountRepository;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService extends AbstractCrudService<AccountDto, AccountEntity> {

  private final AccountRepository repository;
  private final AccountMapper mapper;

  @Override
  protected BeanConverter<AccountDto, AccountEntity> getConverter() {
    return mapper;
  }

  @Override
  protected JpaRepository<AccountEntity, Long> getRepository() {
    return repository;
  }

  @Override
  protected AccountEntity updateEntityWithDto(@NonNull AccountEntity entity,
      @NonNull AccountDto dto) {
    mapper.applyUpdateFromDto(dto, entity);
    return entity;
  }

  @Transactional
  public AccountResponse create(@NonNull AccountCreateRequest request) {
    final String name = request.name() == null ? null : request.name().trim();
    if (repository.existsByNameIgnoreCase(name)) {
      throw new AppException(MessageCodes.ACCOUNT_ALREADY_EXISTS, name);
    }
    AccountDto dto = mapper.fromCreateRequest(new AccountCreateRequest(name));
    return mapper.toResponse(save(dto));
  }

  @Transactional
  public AccountResponse update(@NonNull Long id, @NonNull AccountUpdateRequest request) {
    AccountDto accountDto = get(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, id));

    final String newName = request.name() == null ? null : request.name().trim();

    if (!accountDto.getName().equalsIgnoreCase(newName)
        && repository.existsByNameIgnoreCase(newName)) {
      throw new AppException(MessageCodes.ACCOUNT_ALREADY_EXISTS, newName);
    }

    mapper.applyUpdate(request, accountDto);

    AccountDto saved = save(accountDto);
    return mapper.toResponse(saved);
  }

  @Override
  @Transactional
  public void delete(@NonNull Long id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, id);
    }
    repository.deleteById(id);
  }

  public boolean exists(Long id) {
    if (id == null) {
      throw new BadRequestException(MessageCodes.ACCOUNT_ID_REQUIRED);
    }
    return repository.existsById(id);
  }
}
