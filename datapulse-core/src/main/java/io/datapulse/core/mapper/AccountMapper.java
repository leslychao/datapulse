package io.datapulse.core.mapper;

import io.datapulse.core.entity.AccountEntity;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = MapStructCentralMapperConfig.class)
public interface AccountMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountEntity mapToEntity(AccountDto dto);

  AccountDto mapToDto(AccountEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountDto fromCreateRequest(AccountCreateRequest request);

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
