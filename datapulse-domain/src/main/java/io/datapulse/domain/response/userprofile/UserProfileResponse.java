package io.datapulse.domain.response.userprofile;

public record UserProfileResponse(
    Long id,
    String keycloakSub,
    String email,
    String fullName,
    String username,
    String createdAt,
    String updatedAt
) {

}
