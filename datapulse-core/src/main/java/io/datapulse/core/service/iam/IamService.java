package io.datapulse.core.service.iam;

import io.datapulse.core.service.userprofile.UserProfileService;
import io.datapulse.domain.identity.AuthenticatedUser;
import io.datapulse.domain.response.account.AccountResponse;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IamService {

  private final UserProfileService userProfileService;
  private final AccessibleAccountsQueryService accessibleAccountsQueryService;

  @Transactional(readOnly = true)
  public UserProfileResponse getCurrentUserProfile(long profileId) {
    return userProfileService.getUserProfileRequired(profileId);
  }

  @Transactional
  public long ensureUserProfileAndGetId(AuthenticatedUser user) {
    return userProfileService.ensureUserProfileAndGetId(
        user.userId(),
        user.email(),
        user.fullName(),
        user.username()
    );
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> getAccessibleActiveAccounts(long profileId) {
    return accessibleAccountsQueryService.findAccessibleActiveAccounts(profileId);
  }
}
