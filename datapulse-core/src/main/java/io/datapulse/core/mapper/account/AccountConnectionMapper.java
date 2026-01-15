package io.datapulse.core.mapper.account;

import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.dto.account.AccountConnectionDto;
import io.datapulse.domain.request.account.AccountConnectionCreateRequest;
import io.datapulse.domain.request.account.AccountConnectionUpdateRequest;
import io.datapulse.domain.response.account.AccountConnectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = TimeMapper.class,
    config = BaseMapperConfig.class
)
public abstract class AccountConnectionMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "account.id", source = "accountId")
  @Mapping(target = "active", expression = "java(dto.getActive() != null ? dto.getActive() : true)")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionEntity toEntity(AccountConnectionDto dto);

  @Mapping(source = "account.id", target = "accountId")
  @Mapping(source = "maskedCredentials", target = "maskedCredentials")
  @Mapping(target = "credentials", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "maskedCredentials", ignore = true)
  @Mapping(target = "active", expression = "java(request.active() != null ? request.active() : true)")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionCreateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "lastSyncAt", ignore = true)
  @Mapping(target = "lastSyncStatus", ignore = true)
  @Mapping(target = "maskedCredentials", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract AccountConnectionDto toDto(AccountConnectionUpdateRequest request);

  @Mapping(source = "maskedCredentials", target = "maskedCredentials")
  public abstract AccountConnectionResponse toResponse(AccountConnectionDto dto);
}
