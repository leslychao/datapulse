package io.datapulse.domain.response.userprofile;

import java.time.OffsetDateTime;

public record UserProfileResponse(
    Long id,
    String keycloakSub,
    String email,
    String fullName,
    String username,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}
