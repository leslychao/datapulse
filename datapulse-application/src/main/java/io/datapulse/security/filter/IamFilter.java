package io.datapulse.security.filter;

import io.datapulse.cache.UserProfileIdCache;
import io.datapulse.core.service.iam.IamService;
import io.datapulse.iam.DomainUserContext;
import io.datapulse.security.SecurityHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class IamFilter extends OncePerRequestFilter {

  private final SecurityHelper securityHelper;
  private final IamService iamService;
  private final UserProfileIdCache userProfileIdCache;
  private final ObjectProvider<DomainUserContext> domainUserContextProvider;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator")
        || path.startsWith("/swagger")
        || path.startsWith("/v3/api-docs");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    securityHelper
        .getCurrentUserIfAuthenticated()
        .ifPresent(user -> {
          long profileId = userProfileIdCache.getOrLoad(
              user.userId(),
              () -> iamService.ensureUserProfileAndGetId(user)
          );
          domainUserContextProvider.getObject().setProfileId(profileId);
        });

    filterChain.doFilter(request, response);
  }
}
