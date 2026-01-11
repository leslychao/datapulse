package io.datapulse.domain.dto.response.me;

import io.datapulse.domain.dto.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Locale;

public record MeResponse(
    String userId,
    String username,
    String email,
    String fullName,
    String givenName,
    String familyName,
    Locale locale,
    Instant authenticatedAt
) {

  public static MeResponse from(AuthenticatedUser user) {
    return new MeResponse(
        user.userId(),
        user.username(),
        user.email(),
        user.fullName(),
        user.givenName(),
        user.familyName(),
        user.locale(),
        user.authenticatedAt()
    );
  }
}
