package io.datapulse.iam.useractivity;

import io.datapulse.core.service.useractivity.UserActivityService;
import io.datapulse.iam.DomainUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class UserActivityInterceptor implements HandlerInterceptor {

  private final DomainUserContext domainUserContext;
  private final UserActivityService userActivityService;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler
  ) {
    domainUserContext.getProfileId().ifPresent(userActivityService::touch);
    return true;
  }
}
