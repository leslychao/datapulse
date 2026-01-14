package io.datapulse.cache;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaffeineUserProfileIdCache implements UserProfileIdCache {

  private final Cache<String, Long> cache;

  @Override
  public long getOrLoad(String keycloakSub, LongSupplier loader) {
    return cache.get(keycloakSub, k -> loader.getAsLong());
  }

  @Override
  public void evict(String keycloakSub) {
    cache.invalidate(keycloakSub);
  }
}
