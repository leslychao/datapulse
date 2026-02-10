package io.datapulse.iam;

import io.datapulse.domain.exception.SecurityException;
import java.util.Optional;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(
    value = WebApplicationContext.SCOPE_REQUEST,
    proxyMode = ScopedProxyMode.TARGET_CLASS
)
public class DomainUserContext {

  private Long profileId;
  private UserPrincipalSnapshot principal;

  public void setProfileId(long profileId) {
    this.profileId = profileId;
  }

  public void setPrincipal(UserPrincipalSnapshot principal) {
    this.principal = principal;
  }

  public long requireProfileId() {
    if (profileId == null) {
      throw SecurityException.userProfileNotResolved();
    }
    return profileId;
  }

  public Optional<Long> getProfileId() {
    return Optional.ofNullable(profileId);
  }

  public Optional<UserPrincipalSnapshot> getPrincipal() {
    return Optional.ofNullable(principal);
  }

  public String requireCurrentEmail() {
    return getPrincipal()
        .map(UserPrincipalSnapshot::email)
        .filter(email -> !email.isBlank())
        .orElseThrow(() -> SecurityException.jwtClaimMissing("email"));
  }

  public record UserPrincipalSnapshot(
      String keycloakSub,
      String username,
      String email,
      String fullName
  ) {

  }
}
