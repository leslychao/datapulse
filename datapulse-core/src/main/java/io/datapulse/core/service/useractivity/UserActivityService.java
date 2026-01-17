package io.datapulse.core.service.useractivity;

import com.github.benmanes.caffeine.cache.Cache;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActivityService {

  private final Cache<Long, Instant> userActivityCache;

  public void touch(long profileId) {
    userActivityCache.put(profileId, Instant.now());
  }

  public boolean isRecentlyActive(long profileId) {
    return userActivityCache.getIfPresent(profileId) != null;
  }
}
