package io.datapulse.core.mapper.account;

import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.response.account.AccountResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface AccountMapper {

  AccountResponse toResponse(AccountEntity entity);
}
