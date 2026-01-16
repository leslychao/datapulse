package io.datapulse.rest.iam;

import io.datapulse.core.service.iam.IamService;
import io.datapulse.domain.response.account.AccountResponse;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import io.datapulse.iam.DomainUserContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/iam", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class IamController {

  private final IamService iamService;
  private final DomainUserContext domainUserContext;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public UserProfileResponse currentUserProfile() {
    long profileId = domainUserContext.requireProfileId();
    return iamService.getCurrentUserProfile(profileId);
  }

  @GetMapping("/accounts")
  @PreAuthorize("isAuthenticated()")
  public List<AccountResponse> getAccessibleActiveAccounts() {
    long profileId = domainUserContext.requireProfileId();
    return iamService.getAccessibleActiveAccounts(profileId);
  }
}
