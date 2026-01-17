package io.datapulse.core.useractivity;

import com.github.benmanes.caffeine.cache.Cache;
import io.datapulse.core.properties.UserActivityProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActivityCacheMaintenance {

  private final Cache<Long, Instant> cache;
  private final UserActivityProperties props;

  @Scheduled(fixedDelayString = "${app.user-activity.cleanup-fixed-delay}")
  public void cleanup() {
    cache.cleanUp();
  }
}
