package io.datapulse.core.mapper;

import io.datapulse.core.entity.AccountEntity;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface AccountMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountEntity toEntity(AccountDto dto);

  AccountDto toDto(AccountEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountDto toDto(AccountCreateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountDto toDto(AccountUpdateRequest request);

  @AfterMapping
  default void normalizeDto(@MappingTarget AccountDto dto) {
    if (dto.getName() != null) {
      dto.setName(StringUtils.trimToNull(dto.getName()));
    }
  }

  AccountResponse toResponse(AccountDto dto);
}
