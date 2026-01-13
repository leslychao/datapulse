package io.datapulse.core.service.userprofile;

import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.userprofile.UserProfileRepository;
import io.datapulse.core.service.AbstractIngestApiService;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.userprofile.UserProfileDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import io.datapulse.domain.request.userprofile.UserProfileCreateRequest;
import io.datapulse.domain.request.userprofile.UserProfileUpdateRequest;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  @Transactional(readOnly = true)
  public UserProfileResponse getByKeycloakSubRequired(
      @NotNull(message = ValidationKeys.USER_PROFILE_KEYCLOAK_SUB_REQUIRED)
      String keycloakSub
  ) {
    String normalized = normalizeKeycloakSubRequired(keycloakSub);

    UserProfileEntity entity = userProfileRepository
        .findByKeycloakSub(normalized)
        .orElseThrow(() -> new NotFoundException(
            MessageCodes.USER_PROFILE_BY_SUB_NOT_FOUND,
            normalized
        ));

    UserProfileDto dto = mapperFacade.to(entity, UserProfileDto.class);
    return toResponse(dto);
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
      throw new BadRequestException(
          MessageCodes.USER_PROFILE_KEYCLOAK_SUB_ALREADY_EXISTS,
          dto.getKeycloakSub()
      );
    }
    if (userProfileRepository.existsByEmailIgnoreCase(dto.getEmail())) {
      throw new BadRequestException(
          MessageCodes.USER_PROFILE_EMAIL_ALREADY_EXISTS,
          dto.getEmail()
      );
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
    if (source.getFullName() != null) {
      target.setFullName(source.getFullName());
    }
    if (source.getUsername() != null) {
      target.setUsername(source.getUsername());
    }
    return target;
  }

  @Override
  protected UserProfileEntity beforeSave(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      UserProfileEntity entity
  ) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);

    return entity;
  }

  @Override
  protected UserProfileEntity beforeUpdate(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      UserProfileEntity entity
  ) {
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    return entity;
  }

  private static String normalizeKeycloakSubRequired(String keycloakSub) {
    if (keycloakSub == null) {
      throw new AppException(MessageCodes.USER_PROFILE_KEYCLOAK_SUB_REQUIRED);
    }
    String normalized = keycloakSub.trim();
    if (normalized.isBlank()) {
      throw new AppException(MessageCodes.USER_PROFILE_KEYCLOAK_SUB_REQUIRED);
    }
    return normalized;
  }
}
