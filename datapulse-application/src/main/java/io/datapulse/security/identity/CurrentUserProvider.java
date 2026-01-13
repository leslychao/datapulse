package io.datapulse.security.identity;

import io.datapulse.domain.exception.SecurityException;
import io.datapulse.domain.identity.AuthenticatedUser;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserProvider {

  public Optional<AuthenticatedUser> getCurrentUserIfAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
      return Optional.empty();
    }
    if (!jwtAuthenticationToken.isAuthenticated()) {
      return Optional.empty();
    }

    Jwt jwt = jwtAuthenticationToken.getToken();

    String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      return Optional.empty();
    }

    String preferredUsername = jwt.getClaimAsString("preferred_username");
    String email = jwt.getClaimAsString("email");
    String name = jwt.getClaimAsString("name");
    String givenName = jwt.getClaimAsString("given_name");
    String familyName = jwt.getClaimAsString("family_name");

    Locale locale = resolveLocale(jwt.getClaimAsString("locale"));
    Instant authTime = jwt.getClaimAsInstant("auth_time");

    return Optional.of(new AuthenticatedUser(
        subject,
        preferredUsername,
        email,
        name,
        givenName,
        familyName,
        locale,
        authTime
    ));
  }

  public AuthenticatedUser getCurrentUser() {
    return getCurrentUserIfAuthenticated().orElseThrow(
        SecurityException::unauthenticatedJwtNotFound);
  }

  public String getCurrentKeycloakSub() {
    JwtAuthenticationToken jwtAuthenticationToken = requireJwtAuthenticationToken();
    String sub = jwtAuthenticationToken.getToken().getSubject();
    return requireClaimValue(sub, "sub");
  }

  public String getCurrentUsername() {
    JwtAuthenticationToken jwtAuthenticationToken = requireJwtAuthenticationToken();
    String username = jwtAuthenticationToken.getToken().getClaimAsString("preferred_username");
    return requireClaimValue(username, "preferred_username");
  }

  private static JwtAuthenticationToken requireJwtAuthenticationToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw SecurityException.unauthenticatedJwtNotFound();
    }
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
      throw SecurityException.unauthenticatedJwtNotFound();
    }
    if (!jwtAuthenticationToken.isAuthenticated()) {
      throw SecurityException.jwtNotAuthenticated();
    }
    return jwtAuthenticationToken;
  }

  private static String requireClaimValue(String value, String claimName) {
    if (value == null || value.isBlank()) {
      throw SecurityException.jwtClaimMissing(claimName);
    }
    return value;
  }

  private static Locale resolveLocale(String localeTag) {
    if (localeTag == null || localeTag.isBlank()) {
      return Locale.ROOT;
    }
    return Locale.forLanguageTag(localeTag);
  }
}
