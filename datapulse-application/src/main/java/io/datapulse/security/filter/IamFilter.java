package io.datapulse.security.filter;

import io.datapulse.cache.UserProfileIdCache;
import io.datapulse.core.service.iam.IamService;
import io.datapulse.domain.identity.AuthenticatedUser;
import io.datapulse.iam.DomainUserContext;
import io.datapulse.security.SecurityHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class IamFilter extends OncePerRequestFilter {

  private final SecurityHelper securityHelper;
  private final IamService iamService;
  private final UserProfileIdCache userProfileIdCache;
  private final DomainUserContext domainUserContext;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }

    String path = request.getRequestURI();
    return path.startsWith("/actuator")
        || path.startsWith("/swagger")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/error");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    securityHelper
        .getCurrentUserIfAuthenticated()
        .ifPresent(user -> resolveCurrentUserProfile(request, user));

    filterChain.doFilter(request, response);
  }

  private void resolveCurrentUserProfile(HttpServletRequest request, AuthenticatedUser user) {
    String keycloakSub = user.keycloakSub();

    try {
      long profileId = userProfileIdCache.getOrLoad(
          keycloakSub,
          () -> iamService.ensureUserProfileAndGetId(user)
      );

      domainUserContext.setProfileId(profileId);
      domainUserContext.setPrincipal(new DomainUserContext.UserPrincipalSnapshot(
          keycloakSub,
          user.username(),
          user.email(),
          user.fullName()
      ));
    } catch (RuntimeException exception) {
      log.error(
          "Failed to resolve domain user profile: keycloakSub='{}', method={}, path={}, reason={}",
          keycloakSub,
          request.getMethod(),
          request.getRequestURI(),
          shortReason(exception),
          exception
      );
      throw exception;
    }
  }

  private static String shortReason(Throwable throwable) {
    if (throwable == null) {
      return "unknown";
    }

    String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable.getClass().getSimpleName();
    }

    return throwable.getClass().getSimpleName() + ": " + message.trim();
  }
}
