package io.datapulse.core.converter;

import io.datapulse.core.entity.AccountEntity;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = MapStructCentralConfig.class)
public interface AccountMapper extends BeanConverter<AccountDto, AccountEntity> {

  @Override
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountEntity mapToEntity(AccountDto dto);

  @Override
  AccountDto mapToDto(AccountEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountDto fromCreateRequest(AccountCreateRequest request);

  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void applyUpdateFromDto(AccountDto dto, @MappingTarget AccountEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void applyUpdate(AccountUpdateRequest request, @MappingTarget AccountDto dto);

  @AfterMapping
  default void normalizeDto(@MappingTarget AccountDto dto) {
    if (dto.getName() != null) {
      dto.setName(dto.getName().trim());
    }
  }

  @AfterMapping
  default void normalizeEntity(@MappingTarget AccountEntity entity) {
    if (entity.getName() != null) {
      entity.setName(entity.getName().trim());
    }
  }

  AccountResponse toResponse(AccountDto dto);
}
