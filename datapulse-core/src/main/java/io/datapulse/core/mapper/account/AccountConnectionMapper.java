package io.datapulse.core.mapper.account;

import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.response.account.AccountConnectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = TimeMapper.class,
    config = BaseMapperConfig.class
)
public interface AccountConnectionMapper {

  @Mapping(target = "accountId", source = "account.id")
  AccountConnectionResponse toResponse(AccountConnectionEntity entity);
}
