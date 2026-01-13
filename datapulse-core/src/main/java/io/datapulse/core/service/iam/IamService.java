package io.datapulse.core.service.iam;

import io.datapulse.core.repository.userprofile.UserProfileRepository;
import io.datapulse.core.service.userprofile.UserProfileService;
import io.datapulse.domain.exception.SecurityException;
import io.datapulse.domain.identity.AuthenticatedUser;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IamService {

  private final UserProfileRepository userProfileRepository;
  private final UserProfileService userProfileService;

  @Transactional(readOnly = true)
  public UserProfileResponse currentUserProfile(String keycloakSub) {
    return userProfileService.getByKeycloakSubRequired(keycloakSub);
  }

  @Transactional
  public void ensureUserProfile(AuthenticatedUser user) {
    String keycloakSub = requireText(user.userId(), "sub");
    String email = requireText(user.email(), "email");

    userProfileRepository.upsertAndSyncIfChangedSafeEmail(
        keycloakSub,
        email,
        user.fullName(),
        user.username()
    );

    userProfileService.getByKeycloakSubRequired(keycloakSub);
  }

  private static String requireText(String value, String claimName) {
    if (value == null || value.isBlank()) {
      throw SecurityException.jwtClaimMissing(claimName);
    }
    return value.trim();
  }
}
