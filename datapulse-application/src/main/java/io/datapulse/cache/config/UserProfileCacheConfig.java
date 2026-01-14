package io.datapulse.cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserProfileCacheConfig {

  @Bean
  public Cache<String, Long> userProfileIdCache(
      @Value("${datapulse.cache.user-profile-id.maximum-size}") long maximumSize,
      @Value("${datapulse.cache.user-profile-id.expire-after-access}") Duration expireAfterAccess
  ) {
    return Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterAccess(expireAfterAccess)
        .build();
  }
}
