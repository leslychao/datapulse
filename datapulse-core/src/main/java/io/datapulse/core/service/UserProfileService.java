package io.datapulse.core.service;

import io.datapulse.core.entity.UserProfileEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.UserProfileRepository;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.UserProfileDto;
import io.datapulse.domain.dto.request.UserProfileCreateRequest;
import io.datapulse.domain.dto.request.UserProfileUpdateRequest;
import io.datapulse.domain.dto.response.UserProfileResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProfileService extends AbstractIngestApiService<
    UserProfileCreateRequest,
    UserProfileUpdateRequest,
    UserProfileResponse,
    UserProfileDto,
    UserProfileEntity> {

  private final UserProfileRepository userProfileRepository;
  private final MapperFacade mapperFacade;

  @Override
  protected MapperFacade mapper() {
    return mapperFacade;
  }

  @Override
  protected JpaRepository<UserProfileEntity, Long> repository() {
    return userProfileRepository;
  }

  @Override
  protected Class<UserProfileDto> dtoType() {
    return UserProfileDto.class;
  }

  @Override
  protected Class<UserProfileEntity> entityType() {
    return UserProfileEntity.class;
  }

  @Override
  protected Class<UserProfileResponse> responseType() {
    return UserProfileResponse.class;
  }

  @Override
  protected void validateOnCreate(UserProfileDto dto) {
    if (dto.getKeycloakSub() == null || dto.getKeycloakSub().isBlank()) {
      throw new AppException(MessageCodes.USER_PROFILE_KEYCLOAK_SUB_REQUIRED);
    }
    if (dto.getEmail() == null || dto.getEmail().isBlank()) {
      throw new AppException(MessageCodes.USER_PROFILE_EMAIL_REQUIRED);
    }

    if (userProfileRepository.existsByKeycloakSub(dto.getKeycloakSub())) {
      throw new BadRequestException(MessageCodes.USER_PROFILE_KEYCLOAK_SUB_ALREADY_EXISTS,
          dto.getKeycloakSub());
    }
    if (userProfileRepository.existsByEmailIgnoreCase(dto.getEmail())) {
      throw new BadRequestException(MessageCodes.USER_PROFILE_EMAIL_ALREADY_EXISTS, dto.getEmail());
    }
  }

  @Override
  protected void validateOnUpdate(Long id, UserProfileDto dto, UserProfileEntity existing) {
    if (dto.getEmail() == null || dto.getEmail().isBlank()) {
      throw new AppException(MessageCodes.USER_PROFILE_EMAIL_REQUIRED);
    }
    if (userProfileRepository.existsByEmailIgnoreCaseAndIdNot(dto.getEmail(), id)) {
      throw new AppException(MessageCodes.USER_PROFILE_EMAIL_ALREADY_EXISTS, dto.getEmail());
    }
  }

  @Override
  protected UserProfileEntity merge(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      UserProfileEntity target,
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      UserProfileDto source
  ) {
    target.setEmail(source.getEmail());
    return target;
  }
}
