package io.datapulse.core.mapper;

import io.datapulse.core.entity.UserProfileEntity;
import io.datapulse.domain.dto.UserProfileDto;
import io.datapulse.domain.dto.request.UserProfileCreateRequest;
import io.datapulse.domain.dto.request.UserProfileUpdateRequest;
import io.datapulse.domain.dto.response.UserProfileResponse;
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
