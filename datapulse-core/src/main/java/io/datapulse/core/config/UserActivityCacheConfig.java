package io.datapulse.core.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.datapulse.core.properties.UserActivityProperties;
import io.datapulse.core.service.useractivity.UserActivityWriteBehindStore;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UserActivityProperties.class)
@RequiredArgsConstructor
public class UserActivityCacheConfig {

  @Bean
  public Cache<Long, Instant> userActivityCache(
      UserActivityWriteBehindStore writeBehindStore,
      UserActivityProperties props
  ) {
    return Caffeine.newBuilder()
        .maximumSize(props.maxSize())
        .expireAfterWrite(props.activityWindow())
        .removalListener((Long profileId, Instant lastSeenAt, RemovalCause cause) -> {
          if (profileId == null || lastSeenAt == null) {
            return;
          }
          if (cause == RemovalCause.EXPIRED) {
            writeBehindStore.enqueue(profileId, lastSeenAt);
          }
        })
        .build();
  }
}
