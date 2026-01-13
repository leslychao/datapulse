package io.datapulse.security.identity.filter;

import io.datapulse.core.service.iam.IamService;
import io.datapulse.security.identity.CurrentUserProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class IamFilter extends OncePerRequestFilter {

  private final CurrentUserProvider currentUserProvider;
  private final IamService iamService;

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

    currentUserProvider
        .getCurrentUserIfAuthenticated()
        .ifPresent(iamService::ensureUserProfile);

    filterChain.doFilter(request, response);
  }
}
