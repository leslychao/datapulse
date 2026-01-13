package io.datapulse.rest.iam;

import io.datapulse.core.service.iam.IamService;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import io.datapulse.security.identity.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/iam", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class IamController {

  private final IamService iamService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping
  public UserProfileResponse currentUserProfile() {
    return iamService.currentUserProfile(currentUserProvider.getCurrentKeycloakSub());
  }
}
