package io.datapulse.core.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.datapulse.core.properties.UserActivityProperties;
import io.datapulse.core.useractivity.UserActivityWriteBehindStore;
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
      UserActivityWriteBehindStore writeBehind,
      UserActivityProperties props) {
    return Caffeine.newBuilder()
        .maximumSize(props.maxSize())
        .expireAfterWrite(props.expireAfterWrite())
        .removalListener((Long profileId, Instant lastSeen, RemovalCause cause) -> {
          if (profileId == null || lastSeen == null) {
            return;
          }
          if (cause == RemovalCause.EXPIRED) {
            writeBehind.enqueue(profileId, lastSeen);
          }
        })
        .build();
  }
}
