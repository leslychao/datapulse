package io.datapulse.core.mapper.account;

import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.dto.account.AccountDto;
import io.datapulse.domain.request.account.AccountCreateRequest;
import io.datapulse.domain.request.account.AccountUpdateRequest;
import io.datapulse.domain.response.account.AccountResponse;
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
