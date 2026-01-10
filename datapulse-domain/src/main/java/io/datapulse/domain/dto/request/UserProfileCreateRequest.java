package io.datapulse.domain.dto.request;

import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserProfileCreateRequest(
    @NotBlank(message = ValidationKeys.USER_PROFILE_KEYCLOAK_SUB_REQUIRED)
    String keycloakSub,

    @NotBlank(message = ValidationKeys.USER_PROFILE_EMAIL_REQUIRED)
    @Email(message = ValidationKeys.USER_PROFILE_EMAIL_INVALID)
    String email,

    String fullName,
    String username
) {

}
