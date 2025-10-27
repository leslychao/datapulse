package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_CREATE_REQUEST_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_UPDATE_REQUEST_REQUIRED;
import static io.datapulse.domain.MessageCodes.ID_REQUIRED;

import io.datapulse.core.converter.AccountMapper;
import io.datapulse.core.converter.BeanConverter;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.repository.AccountRepository;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class AccountService extends AbstractCrudService<AccountDto, AccountEntity> {

  private final AccountRepository repository;
  private final AccountMapper mapper;

  private static String normalizeName(String raw) {
    return StringUtils.trim(raw);
  }

  @Override
  protected AccountEntity entityPreSaveAction(AccountEntity entity) {
    var now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(now);
    }
    entity.setUpdatedAt(now);
    return super.entityPreSaveAction(entity);
  }

  @Override
  protected AccountEntity entityPreUpdateAction(AccountEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return super.entityPreUpdateAction(entity);
  }

  @Override
  protected BeanConverter<AccountDto, AccountEntity> getConverter() {
    return mapper;
  }

  @Override
  protected JpaRepository<AccountEntity, Long> getRepository() {
    return repository;
  }

  @Override
  protected AccountEntity updateEntityWithDto(
      AccountEntity entity,
      AccountDto dto) {
    mapper.applyUpdateFromDto(dto, entity);
    return entity;
  }

  @Transactional
  public AccountResponse create(
      @NotNull(message = ACCOUNT_CREATE_REQUEST_REQUIRED) AccountCreateRequest request) {
    AccountDto dto = mapper.fromCreateRequest(request);
    if (repository.existsByNameIgnoreCase(dto.getName())) {
      throw new AppException(MessageCodes.ACCOUNT_ALREADY_EXISTS, dto.getName());
    }
    return mapper.toResponse(save(dto));
  }

  @Transactional
  public AccountResponse update(
      @NotNull(message = ID_REQUIRED) Long id,
      @NotNull(message = ACCOUNT_UPDATE_REQUEST_REQUIRED) AccountUpdateRequest request) {
    AccountDto current = get(id).orElseThrow(
        () -> new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, id));

    if (!StringUtils.equalsIgnoreCase(current.getName(), request.name())
        && repository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
      throw new AppException(MessageCodes.ACCOUNT_ALREADY_EXISTS, request.name());
    }

    mapper.applyUpdate(request, current);
    return mapper.toResponse(update(current));
  }

  @Override
  @Transactional
  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    try {
      repository.deleteById(id);
    } catch (EmptyResultDataAccessException e) {
      throw new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, id);
    }
  }

  @Transactional(readOnly = true)
  public boolean exists(@NotNull(message = ID_REQUIRED) Long id) {
    return repository.existsById(id);
  }
}
