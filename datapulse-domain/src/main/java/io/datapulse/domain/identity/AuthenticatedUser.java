package io.datapulse.domain.identity;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public record AuthenticatedUser(
    String userId,
    String username,
    String email,
    String fullName,
    String givenName,
    String familyName,
    Locale locale,
    Instant authenticatedAt
) {

}
