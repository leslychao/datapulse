package io.datapulse.core.service;

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
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import java.time.OffsetDateTime;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService extends AbstractCrudService<AccountDto, AccountEntity> {

  private final AccountRepository repository;

  @Override
  protected AccountEntity entityPreSaveAction(@NonNull AccountEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    entity.setCreatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return super.entityPreSaveAction(entity);
  }

  @Override
  protected AccountEntity entityPreUpdateAction(@NonNull AccountEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return super.entityPreUpdateAction(entity);
  }

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
    if (StringUtils.isBlank(request.name())) {
      throw new BadRequestException(MessageCodes.ACCOUNT_NAME_REQUIRED);
    }

    final String name = request.name().trim();

    if (repository.existsByNameIgnoreCase(name)) {
      throw new AppException(MessageCodes.ACCOUNT_ALREADY_EXISTS, name);
    }
    AccountDto dto = mapper.fromCreateRequest(request);
    return mapper.toResponse(save(dto));
  }

  @Transactional
  public AccountResponse update(@NonNull Long id, @NonNull AccountUpdateRequest request) {
    if (StringUtils.isBlank(request.name())) {
      throw new BadRequestException(MessageCodes.ACCOUNT_NAME_REQUIRED);
    }

    AccountDto accountDto = get(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.ACCOUNT_NOT_FOUND, id));

    final String newName = request.name().trim();

    if (!StringUtils.equalsIgnoreCase(accountDto.getName(), newName)
        && repository.existsByNameIgnoreCaseAndIdNot(newName, id)) {
      throw new AppException(MessageCodes.ACCOUNT_ALREADY_EXISTS, newName);
    }

    mapper.applyUpdate(request, accountDto);
    return mapper.toResponse(update(accountDto));
  }

  @Override
  public void delete(@NonNull Long id) {
    if (!exists(id)) {
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
