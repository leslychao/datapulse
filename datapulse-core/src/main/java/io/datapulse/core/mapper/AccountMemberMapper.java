package io.datapulse.core.mapper;

import io.datapulse.core.entity.AccountMemberEntity;
import io.datapulse.core.service.useractivity.UserActivityService;
import io.datapulse.domain.response.AccountMemberResponse;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public abstract class AccountMemberMapper {

  @Autowired
  private UserActivityService userActivityService;

  @Mapping(target = "accountId", source = "account.id")
  @Mapping(target = "userId", source = "user.id")

  @Mapping(target = "email", source = "user.email")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "fullName", source = "user.fullName")

  @Mapping(target = "recentlyActive", expression = "java(isRecentlyActive(entity))")
  @Mapping(target = "lastActivityAt", expression = "java(mapLastActivityAt(entity))")
  public abstract AccountMemberResponse toResponse(AccountMemberEntity entity);

  protected boolean isRecentlyActive(AccountMemberEntity entity) {
    Long profileId = entity.getUser().getId();
    return userActivityService.isRecentlyActive(profileId);
  }

  protected OffsetDateTime mapLastActivityAt(AccountMemberEntity entity) {
    Long profileId = entity.getUser().getId();
    boolean recentlyActive = userActivityService.isRecentlyActive(profileId);
    if (recentlyActive) {
      return null;
    }
    return entity.getUser().getLastActivityAt();
  }
}
