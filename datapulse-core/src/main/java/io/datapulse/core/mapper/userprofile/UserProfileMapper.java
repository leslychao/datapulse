package io.datapulse.core.mapper.userprofile;

import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.dto.userprofile.UserProfileDto;
import io.datapulse.domain.request.userprofile.UserProfileCreateRequest;
import io.datapulse.domain.request.userprofile.UserProfileUpdateRequest;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface UserProfileMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserProfileEntity toEntity(UserProfileDto dto);

  UserProfileDto toDto(UserProfileEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserProfileDto toDto(UserProfileCreateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "keycloakSub", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserProfileDto toDto(UserProfileUpdateRequest request);

  @AfterMapping
  default void normalizeDto(@MappingTarget UserProfileDto dto) {
    if (dto.getKeycloakSub() != null) {
      dto.setKeycloakSub(StringUtils.trimToNull(dto.getKeycloakSub()));
    }
    if (dto.getEmail() != null) {
      dto.setEmail(StringUtils.trimToNull(dto.getEmail()));
    }
    if (dto.getFullName() != null) {
      dto.setFullName(StringUtils.trimToNull(dto.getFullName()));
    }
    if (dto.getUsername() != null) {
      dto.setUsername(StringUtils.trimToNull(dto.getUsername()));
    }
  }

  UserProfileResponse toResponse(UserProfileDto dto);
}
