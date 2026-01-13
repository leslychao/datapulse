package io.datapulse.domain.request.userprofile;

import static io.datapulse.domain.ValidationKeys.USER_PROFILE_EMAIL_INVALID;
import static io.datapulse.domain.ValidationKeys.USER_PROFILE_EMAIL_REQUIRED;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserProfileUpdateRequest(
    @NotBlank(message = USER_PROFILE_EMAIL_REQUIRED)
    @Email(message = USER_PROFILE_EMAIL_INVALID)
    String email,

    String fullName,
    String username
) {

}
