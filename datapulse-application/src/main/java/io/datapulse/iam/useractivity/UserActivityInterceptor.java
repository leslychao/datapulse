package io.datapulse.iam.useractivity;

import io.datapulse.core.service.useractivity.UserActivityService;
import io.datapulse.iam.DomainUserContext;
import jakarta.annotation.Nullable;
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
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      HttpServletResponse response,
      @NonNull Object handler,
      @Nullable Exception ex) {
    if (response.getStatus() >= 400) {
      return;
    }
    domainUserContext.getProfileId().ifPresent(userActivityService::touch);
  }
}
