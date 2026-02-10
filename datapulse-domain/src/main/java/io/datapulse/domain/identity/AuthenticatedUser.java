package io.datapulse.domain.identity;

import java.time.Instant;
import java.util.Locale;

public record AuthenticatedUser(
    String keycloakSub,
    String username,
    String email,
    String fullName,
    String givenName,
    String familyName,
    Locale locale,
    Instant authenticatedAt
) {

}
