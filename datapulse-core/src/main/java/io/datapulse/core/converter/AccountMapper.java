package io.datapulse.core.converter;

import io.datapulse.core.entity.AccountEntity;
import io.datapulse.domain.dto.AccountDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper extends BeanConverter<AccountDto, AccountEntity> {

  @Override
  AccountEntity mapToEntity(AccountDto dto);

  @Override
  AccountDto mapToDto(AccountEntity entity);
}
