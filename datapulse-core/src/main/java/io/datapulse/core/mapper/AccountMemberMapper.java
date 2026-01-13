package io.datapulse.core.mapper;

import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.entity.account.AccountMemberEntity;
import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.domain.dto.AccountMemberDto;
import io.datapulse.domain.request.AccountMemberCreateRequest;
import io.datapulse.domain.request.AccountMemberUpdateRequest;
import io.datapulse.domain.response.AccountMemberResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface AccountMemberMapper {

  // ===== Entity -> DTO =====

  @Mapping(target = "accountId", source = "account.id")
  @Mapping(target = "userId", source = "user.id")
  AccountMemberDto toDto(AccountMemberEntity entity);

  // ===== DTO -> Entity =====

  @Mapping(target = "account", source = "accountId", qualifiedByName = "toAccountRef")
  @Mapping(target = "user", source = "userId", qualifiedByName = "toUserRef")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountMemberEntity toEntity(AccountMemberDto dto);

  // ===== Requests -> DTO =====

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountMemberDto toDto(AccountMemberCreateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AccountMemberDto toDto(AccountMemberUpdateRequest request);

  // ===== DTO -> Response =====

  @Mapping(target = "role", expression = "java(dto.getRole() == null ? null : dto.getRole().name())")
  @Mapping(target = "status", expression = "java(dto.getStatus() == null ? null : dto.getStatus().name())")
  AccountMemberResponse toResponse(AccountMemberDto dto);

  // ===== helpers: Long -> ref-entity =====

  @Named("toAccountRef")
  default AccountEntity toAccountRef(Long accountId) {
    if (accountId == null) {
      return null;
    }
    AccountEntity entity = new AccountEntity();
    entity.setId(accountId);
    return entity;
  }

  @Named("toUserRef")
  default UserProfileEntity toUserRef(Long userId) {
    if (userId == null) {
      return null;
    }
    UserProfileEntity entity = new UserProfileEntity();
    entity.setId(userId);
    return entity;
  }
}
