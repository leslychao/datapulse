package io.datapulse.security.identity;

import io.datapulse.domain.dto.security.AuthenticatedUser;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

  public AuthenticatedUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
      throw new IllegalStateException(
          "Authenticated JWT user not found in SecurityContext."
      );
    }

    return new AuthenticatedUser(
        jwt.getToken().getSubject(),
        jwt.getToken().getClaimAsString("preferred_username"),
        jwt.getToken().getClaimAsString("email"),
        jwt.getToken().getClaimAsString("name"),
        jwt.getToken().getClaimAsString("given_name"),
        jwt.getToken().getClaimAsString("family_name"),
        Locale.forLanguageTag(jwt.getToken().getClaimAsString("locale")),
        jwt.getToken().getClaimAsInstant("auth_time")
    );
  }
}
