package io.datapulse.rest;

import io.datapulse.core.service.account.UserProfileProvisioningService;
import io.datapulse.domain.dto.response.me.MeResponse;
import io.datapulse.domain.dto.security.AuthenticatedUser;
import io.datapulse.security.identity.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/me",
    produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class MeController {

  private final CurrentUserProvider currentUserProvider;
  private final UserProfileProvisioningService userProfileProvisioningService;

  @GetMapping
  public MeResponse me() {
    AuthenticatedUser currentUser = currentUserProvider.currentUser();
    userProfileProvisioningService.ensureUserProfile(currentUser);
    return MeResponse.from(currentUser);
  }
}
