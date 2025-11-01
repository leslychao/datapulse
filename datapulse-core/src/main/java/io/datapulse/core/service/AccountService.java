package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_ALREADY_EXISTS;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CREATE_REQUEST_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ACCOUNT_UPDATE_REQUEST_REQUIRED;
import static io.datapulse.domain.MessageCodes.ID_REQUIRED;

import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.mapper.MapStructCentralMapperConfig;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.AccountRepository;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AccountService extends AbstractIngestApiService<
    AccountCreateRequest,
    AccountUpdateRequest,
    AccountResponse,
    AccountDto,
    AccountEntity> {

  private final AccountRepository repository;
  private final MapperFacade mapperFacade;
  private final AccountApplier accountApplier;

  public AccountService(
      MapperFacade mapperFacade,
      AccountRepository repository,
      AccountApplier accountApplier) {
    this.repository = repository;
    this.mapperFacade = mapperFacade;
    this.accountApplier = accountApplier;
  }

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected JpaRepository<AccountEntity, Long> repository() {
    return repository;
  }

  @Override
  protected Class<AccountDto> dtoType() {
    return AccountDto.class;
  }

  @Override
  protected Class<AccountEntity> entityType() {
    return AccountEntity.class;
  }

  @Override
  protected Class<AccountResponse> responseClass() {
    return AccountResponse.class;
  }

  @Override
  protected AccountEntity beforeSave(AccountEntity entity) {
    var now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(now);
    }
    entity.setUpdatedAt(now);
    return entity;
  }

  @Override
  protected AccountEntity beforeUpdate(AccountEntity entity) {
    entity.setUpdatedAt(OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT));
    return entity;
  }

  @Override
  protected AccountEntity merge(AccountEntity target, AccountDto source) {
    accountApplier.applyUpdateFromDto(source, target);
    return target;
  }

  @Transactional
  public AccountResponse create(
      @Valid @NotNull(message = ACCOUNT_CREATE_REQUEST_REQUIRED) AccountCreateRequest request) {
    var draft = mapper().to(request, dtoType());
    if (repository.existsByNameIgnoreCase(draft.getName())) {
      throw new BadRequestException(ACCOUNT_ALREADY_EXISTS, draft.getName());
    }
    return mapper().to(save(draft), responseClass());
  }

  @Transactional
  public AccountResponse update(
      @NotNull(message = ID_REQUIRED) Long id,
      @Valid @NotNull(message = ACCOUNT_UPDATE_REQUEST_REQUIRED) AccountUpdateRequest request) {

    var current = get(id).orElseThrow(() -> new NotFoundException(ACCOUNT_NOT_FOUND, id));

    if (!StringUtils.equalsIgnoreCase(current.getName(), request.name())
        && repository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
      throw new BadRequestException(ACCOUNT_ALREADY_EXISTS, request.name());
    }

    accountApplier.applyUpdate(request, current);
    return mapper().to(update(current), responseClass());
  }

  @Override
  @Transactional
  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException(ACCOUNT_NOT_FOUND, id);
    }
    repository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public boolean exists(@NotNull(message = ID_REQUIRED) Long id) {
    return repository.existsById(id);
  }

  @Mapper(componentModel = "spring", config = MapStructCentralMapperConfig.class)
  public interface AccountApplier {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void applyUpdateFromDto(AccountDto dto, @MappingTarget AccountEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void applyUpdate(AccountUpdateRequest request, @MappingTarget AccountDto dto);
  }
}
