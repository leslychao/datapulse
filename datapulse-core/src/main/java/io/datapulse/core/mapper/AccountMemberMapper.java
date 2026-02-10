package io.datapulse.core.mapper;

import io.datapulse.core.entity.AccountMemberEntity;
import io.datapulse.domain.response.AccountMemberResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface AccountMemberMapper {

  @Mapping(target = "id", source = "id")

  @Mapping(target = "accountId", source = "account.id")
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "keycloakSub", source = "user.keycloakSub")

  @Mapping(target = "email", source = "user.email")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "fullName", source = "user.fullName")

  @Mapping(target = "role", source = "role")
  @Mapping(target = "status", source = "status")

  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "updatedAt", source = "updatedAt")

  @Mapping(target = "recentlyActive", ignore = true)
  @Mapping(target = "lastActivityAt", ignore = true)
  AccountMemberResponse toResponse(AccountMemberEntity entity);
}
