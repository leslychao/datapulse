package io.datapulse.core.useractivity;

import com.github.benmanes.caffeine.cache.Cache;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActivityCacheMaintenance {

  private final Cache<Long, Instant> cache;

  @Scheduled(fixedDelayString = "PT1M")
  public void cleanup() {
    cache.cleanUp();
  }
}
