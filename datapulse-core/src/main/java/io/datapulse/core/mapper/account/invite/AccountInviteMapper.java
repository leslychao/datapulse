package io.datapulse.core.mapper.account.invite;

import io.datapulse.core.entity.account.invite.AccountInviteEntity;
import io.datapulse.core.entity.account.invite.AccountInviteTargetEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.response.account.invite.AccountInviteResolveResponse;
import io.datapulse.domain.response.account.invite.AccountInviteResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", config = BaseMapperConfig.class)
public interface AccountInviteMapper {

  @Mapping(target = "inviteId", source = "invite.id")
  @Mapping(target = "status", source = "invite.status")
  @Mapping(target = "email", source = "invite.email")
  @Mapping(target = "expiresAt", source = "invite.expiresAt")
  @Mapping(target = "targets", source = "targets")
  AccountInviteResponse toResponse(AccountInviteEntity invite,
      List<AccountInviteTargetEntity> targets);

  @Mapping(target = "accountId", source = "accountId")
  @Mapping(target = "initialRole", source = "initialRole", qualifiedByName = "roleToName")
  AccountInviteResponse.Target toResponseTarget(AccountInviteTargetEntity target);

  @Mapping(target = "accountId", source = "accountId")
  @Mapping(target = "initialRole", source = "initialRole", qualifiedByName = "roleToName")
  AccountInviteResolveResponse.Target toResolveTarget(AccountInviteTargetEntity target);

  default AccountInviteResolveResponse toResolveResponse(
      AccountInviteResolveResponse.ResolveState state,
      AccountInviteEntity invite,
      List<AccountInviteTargetEntity> targets
  ) {
    String email = invite == null ? null : invite.getEmail();
    OffsetDateTime expiresAt = invite == null ? null : invite.getExpiresAt();

    List<AccountInviteResolveResponse.Target> responseTargets = targets == null
        ? List.of()
        : targets.stream().map(this::toResolveTarget).toList();

    return new AccountInviteResolveResponse(state, email, expiresAt, responseTargets);
  }

  @Named("roleToName")
  static String roleToName(AccountMemberRole role) {
    return role == null ? null : role.name();
  }
}
