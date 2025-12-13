package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_ALREADY_EXISTS;
import static io.datapulse.domain.MessageCodes.ACCOUNT_NAME_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ID_REQUIRED;

import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.AccountRepository;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
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
  protected Class<AccountResponse> responseType() {
    return AccountResponse.class;
  }

  @Override
  protected void validateOnCreate(AccountDto draft) {
    String name = draft.getName();
    if (name == null) {
      throw new BadRequestException(ACCOUNT_NAME_REQUIRED);
    }
    if (repository.existsByNameIgnoreCase(name)) {
      throw new BadRequestException(ACCOUNT_ALREADY_EXISTS, name);
    }
  }

  @Override
  protected void validateOnUpdate(Long id, AccountDto dto, AccountEntity existing) {
    String newName = StringUtils.trimToNull(dto.getName());
    if (newName == null) {
      throw new BadRequestException(ACCOUNT_NAME_REQUIRED);
    }
    String oldName = StringUtils.trimToNull(existing.getName());
    if (!StringUtils.equalsIgnoreCase(newName, oldName)) {
      if (repository.existsByNameIgnoreCaseAndIdNot(newName, id)) {
        throw new BadRequestException(ACCOUNT_ALREADY_EXISTS, newName);
      }
    }
  }

  @Override
  protected AccountEntity beforeSave(AccountEntity entity) {
    var now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    entity.setActive(true);
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

  @Override
  @Transactional
  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException(ACCOUNT_NOT_FOUND, id);
    }
    repository.deleteById(id);
  }

  public boolean exists(@NotNull(message = ID_REQUIRED) Long id) {
    return repository.existsById(id);
  }

  public List<AccountDto> getActive() {
    return streamActive()
        .map(accountEntity -> mapperFacade.to(accountEntity, AccountDto.class)).toList();
  }

  public List<Long> getActiveIds() {
    return streamActive()
        .map(accountEntity -> mapperFacade.to(accountEntity, AccountDto.class))
        .map(AccountDto::getId)
        .toList();
  }

  public Stream<AccountDto> streamActive() {
    return repository.findAllByActiveIsTrue().stream()
        .map(accountEntity -> mapperFacade.to(accountEntity, AccountDto.class));
  }

  @Mapper(componentModel = "spring", config = BaseMapperConfig.class)
  public interface AccountApplier {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void applyUpdateFromDto(AccountDto dto, @MappingTarget AccountEntity entity);
  }
}
