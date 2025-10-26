package io.datapulse.core.converter;

import io.datapulse.core.entity.AccountEntity;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper extends BeanConverter<AccountDto, AccountEntity> {

  @Override
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", expression = "java(java.time.OffsetDateTime.now(io.datapulse.domain.CommonConstants.ZONE_ID_DEFAULT))")
  @Mapping(target = "updatedAt", ignore = true)
  AccountEntity mapToEntity(AccountDto dto);

  @Override
  AccountDto mapToDto(AccountEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "name", source = "name")
  @Mapping(target = "createdAt", expression = "java(java.time.OffsetDateTime.now(io.datapulse.domain.CommonConstants.ZONE_ID_DEFAULT))")
  @Mapping(target = "updatedAt", ignore = true)
  AccountDto fromCreateRequest(AccountCreateRequest request);

  default AccountResponse toResponse(AccountDto dto) {
    return new AccountResponse(
        dto.getId(),
        dto.getName(),
        dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null
    );
  }
}
