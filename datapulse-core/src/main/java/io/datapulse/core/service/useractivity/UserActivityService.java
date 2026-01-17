package io.datapulse.core.service.useractivity;

import com.github.benmanes.caffeine.cache.Cache;
import io.datapulse.core.properties.UserActivityProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActivityService {

  private final Cache<Long, Instant> userActivityCache;
  private final UserActivityProperties props;

  public void touch(long profileId) {
    userActivityCache.put(profileId, Instant.now());
  }

  public boolean isRecentlyActive(long profileId) {
    Instant lastSeenAt = userActivityCache.getIfPresent(profileId);
    if (lastSeenAt == null) {
      return false;
    }

    Instant cutoff = Instant.now().minus(props.activityWindow());
    return !lastSeenAt.isBefore(cutoff);
  }
}
