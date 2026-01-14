package io.datapulse.iam;

import io.datapulse.domain.exception.SecurityException;
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

  public void setProfileId(long profileId) {
    this.profileId = profileId;
  }

  public long requireProfileId() {
    if (profileId == null) {
      throw SecurityException.userProfileNotResolved();
    }
    return profileId;
  }
}
