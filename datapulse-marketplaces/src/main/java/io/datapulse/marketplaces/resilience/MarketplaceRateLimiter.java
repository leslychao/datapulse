package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplaceRateLimiter {

  private static final long NANOS_IN_SECOND = 1_000_000_000L;

  private final MarketplaceProperties marketplaceProperties;
  private final Map<String, AtomicLong> scheduleByKey = new ConcurrentHashMap<>();

  public void ensurePermit(MarketplaceType marketplace, EndpointKey endpoint, long accountId) {
    MarketplaceProperties.Provider providerConfig = marketplaceProperties.get(marketplace);
    if (providerConfig == null) {
      throw new IllegalArgumentException(
          "Marketplace %s is not configured in MarketplaceProperties".formatted(
              marketplace.name())
      );
    }

    MarketplaceProperties.EndpointConfig endpointConfig = providerConfig.endpointConfig(endpoint);
    if (endpointConfig == null) {
      throw new IllegalArgumentException(
          "Endpoint %s is not configured for marketplace %s".formatted(
              endpoint.name(), marketplace.name())
      );
    }

    String groupName = endpointConfig.getRateLimitGroup();
    if (groupName == null || groupName.isBlank()) {
      return;
    }

    MarketplaceProperties.RateLimit rateLimitConfig = providerConfig.getRateLimit();
    Map<String, MarketplaceProperties.RateLimitConfig> groups =
        rateLimitConfig != null && rateLimitConfig.getGroups() != null
            ? rateLimitConfig.getGroups()
            : Map.of();

    MarketplaceProperties.RateLimitConfig groupConfig = groups.get(groupName);
    if (groupConfig == null) {
      throw new IllegalArgumentException(
          "Rate limit group %s is not defined for marketplace %s".formatted(
              groupName, marketplace.name())
      );
    }

    int limit = groupConfig.limit();
    Duration period = groupConfig.period();

    if (limit <= 0) {
      throw new IllegalArgumentException(
          "Rate limit for group %s of marketplace %s must be > 0, but was %d".formatted(
              groupName, marketplace.name(), limit)
      );
    }

    if (period == null || period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException(
          "Rate limit period for group %s of marketplace %s must be positive".formatted(
              groupName, marketplace.name())
      );
    }

    long periodNanos = period.toNanos();
    long intervalNanos = Math.max(1L, periodNanos / (long) limit);

    AtomicLong lastAccepted = scheduleByKey.computeIfAbsent(
        bucketKey(marketplace, groupName, accountId),
        key -> new AtomicLong(0L)
    );

    while (true) {
      long now = System.nanoTime();
      long previous = lastAccepted.get();

      long earliestAllowed;
      if (previous <= 0L) {
        earliestAllowed = now;
      } else {
        long maxSafe = Long.MAX_VALUE - intervalNanos;
        long base = Math.min(previous, maxSafe);
        earliestAllowed = base + intervalNanos;
      }

      if (earliestAllowed > now) {
        long waitNanos = earliestAllowed - now;
        int retryAfterSeconds =
            (int) Math.max(1L, Math.ceil(waitNanos / (double) NANOS_IN_SECOND));

        String message =
            "Rate limit exceeded. " +
                "marketplace=%s, endpoint=%s, group=%s, retryAfterSeconds=%s"
                    .formatted(
                        marketplace.name(),
                        endpoint.name(),
                        groupName,
                        retryAfterSeconds
                    );
        throw new TooManyRequestsBackoffRequiredException(
            marketplace,
            endpoint,
            retryAfterSeconds,
            message
        );
      }

      if (lastAccepted.compareAndSet(previous, now)) {
        return;
      }
    }
  }

  private static String bucketKey(MarketplaceType marketplace, String group, long accountId) {
    return marketplace.name() + ":" + group + ":" + accountId;
  }
}
