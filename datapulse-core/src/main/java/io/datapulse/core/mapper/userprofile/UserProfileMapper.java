package io.datapulse.core.mapper.userprofile;

import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", config = BaseMapperConfig.class)
public interface UserProfileMapper {

  UserProfileResponse toResponse(UserProfileEntity dto);
}
