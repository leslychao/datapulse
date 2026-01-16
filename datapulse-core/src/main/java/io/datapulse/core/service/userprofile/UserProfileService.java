package io.datapulse.core.service.userprofile;

import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.userprofile.UserProfileRepository;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.NotFoundException;
import io.datapulse.domain.request.userprofile.UserProfileUpdateRequest;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProfileService {

  private final UserProfileRepository userProfileRepository;
  private final MapperFacade mapperFacade;

  @Transactional
  public long ensureUserProfileAndGetId(
      @NotNull(message = ValidationKeys.REQUEST_REQUIRED) String keycloakSub,
      @NotNull(message = ValidationKeys.REQUEST_REQUIRED) String email,
      String fullName,
      String username
  ) {
    String normalizedKeycloakSub = StringUtils.trimToNull(keycloakSub);
    if (normalizedKeycloakSub == null) {
      throw new BadRequestException(MessageCodes.USER_PROFILE_KEYCLOAK_SUB_REQUIRED);
    }

    String normalizedEmail = StringUtils.trimToNull(email);
    if (normalizedEmail == null) {
      throw new BadRequestException(MessageCodes.USER_PROFILE_EMAIL_REQUIRED);
    }

    return userProfileRepository.upsertAndSyncIfChangedSafeEmailReturningId(
        normalizedKeycloakSub,
        normalizedEmail,
        StringUtils.trimToNull(fullName),
        StringUtils.trimToNull(username)
    );
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getResponseRequired(
      @NotNull(message = ValidationKeys.ID_REQUIRED) Long id
  ) {
    UserProfileEntity entity = userProfileRepository.findById(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.USER_PROFILE_BY_ID_NOT_FOUND, id));

    return mapperFacade.to(entity, UserProfileResponse.class);
  }

  @Transactional
  public UserProfileResponse updateFromRequest(
      @NotNull(message = ValidationKeys.ID_REQUIRED) Long id,
      @Valid @NotNull(message = ValidationKeys.REQUEST_REQUIRED) UserProfileUpdateRequest request
  ) {
    UserProfileEntity existing = userProfileRepository.findById(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.USER_PROFILE_BY_ID_NOT_FOUND, id));

    String email = StringUtils.trimToNull(request.email());
    if (email == null) {
      throw new BadRequestException(MessageCodes.USER_PROFILE_EMAIL_REQUIRED);
    }
    if (userProfileRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
      throw new BadRequestException(MessageCodes.USER_PROFILE_EMAIL_ALREADY_EXISTS, email);
    }

    existing.setEmail(email);

    String fullName = StringUtils.trimToNull(request.fullName());
    if (fullName != null) {
      existing.setFullName(fullName);
    }

    String username = StringUtils.trimToNull(request.username());
    if (username != null) {
      existing.setUsername(username);
    }

    UserProfileEntity saved = userProfileRepository.save(existing);
    return mapperFacade.to(saved, UserProfileResponse.class);
  }
}
