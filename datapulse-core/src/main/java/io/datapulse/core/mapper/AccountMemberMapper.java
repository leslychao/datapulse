package io.datapulse.core.mapper;

import io.datapulse.core.entity.AccountMemberEntity;
import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import io.datapulse.domain.response.AccountMemberResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface AccountMemberMapper {

  @Mapping(target = "accountId", source = "account.id")
  @Mapping(target = "userId", source = "user.id")
  AccountMemberResponse toResponse(AccountMemberEntity entity);

  default String map(AccountMemberRole role) {
    return role == null ? null : role.name();
  }

  default String map(AccountMemberStatus status) {
    return status == null ? null : status.name();
  }
}
