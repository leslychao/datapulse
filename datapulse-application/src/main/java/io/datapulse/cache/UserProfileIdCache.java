package io.datapulse.cache;

import java.util.function.LongSupplier;

public interface UserProfileIdCache {

  long getOrLoad(String keycloakSub, LongSupplier loader);

  void evict(String keycloakSub);
}
