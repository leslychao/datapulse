package io.datapulse.sellerops.domain;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChSafeQuery {

  private static final Logger log = LoggerFactory.getLogger(ChSafeQuery.class);

  private ChSafeQuery() {}

  public static <T> T getOrFallback(Supplier<T> query, T fallback, String operation) {
    try {
      return query.get();
    } catch (Exception e) {
      log.warn("ClickHouse query failed: operation={}, error={}", operation, e.getMessage());
      return fallback;
    }
  }
}
