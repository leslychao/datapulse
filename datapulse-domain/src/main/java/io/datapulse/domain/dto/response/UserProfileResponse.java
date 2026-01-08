package io.datapulse.domain.dto.response;

public record UserProfileResponse(
    Long id,
    String keycloakSub,
    String email,
    String createdAt,
    String updatedAt
) {

}
