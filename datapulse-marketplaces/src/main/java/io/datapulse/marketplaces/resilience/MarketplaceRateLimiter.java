package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplaceRateLimiter {

  private static final long NANOS_IN_SECOND = 1_000_000_000L;

  private final MarketplaceProperties marketplaceProperties;
  private final Map<String, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();

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
    if (periodNanos <= 0L) {
      throw new IllegalArgumentException(
          "Rate limit period for group %s of marketplace %s must be > 0ns".formatted(
              groupName, marketplace.name())
      );
    }

    double tokensPerNano = (double) limit / (double) periodNanos;
    String key = bucketKey(marketplace, groupName, accountId);

    AtomicReference<BucketState> ref = buckets.computeIfAbsent(
        key,
        k -> new AtomicReference<>(new BucketState(System.nanoTime(), limit))
    );

    while (true) {
      long now = System.nanoTime();
      BucketState current = ref.get();

      long lastRefill = current.lastRefillNanos();
      double tokens = current.availableTokens();

      if (now > lastRefill) {
        long elapsedNanos = now - lastRefill;
        double refill = elapsedNanos * tokensPerNano;
        if (refill > 0.0d) {
          tokens = Math.min(limit, tokens + refill);
          lastRefill = now;
        }
      }

      if (tokens >= 1.0d) {
        BucketState updated = new BucketState(lastRefill, tokens - 1.0d);
        if (ref.compareAndSet(current, updated)) {
          return;
        }
        continue;
      }

      double missingTokens = 1.0d - tokens;
      long nanosToWait = (long) Math.ceil(missingTokens / tokensPerNano);
      int retryAfterSeconds = nanosToSecondsCeil(nanosToWait);

      String message =
          "Rate limit exceeded (token bucket). " +
              "marketplace=%s, endpoint=%s, group=%s, limit=%d, period=%ss, retryAfterSeconds=%s"
                  .formatted(
                      marketplace.name(),
                      endpoint.name(),
                      groupName,
                      limit,
                      period.getSeconds(),
                      retryAfterSeconds
                  );

      throw new TooManyRequestsBackoffRequiredException(
          marketplace,
          endpoint,
          retryAfterSeconds,
          message
      );
    }
  }

  private static String bucketKey(MarketplaceType marketplace, String group, long accountId) {
    return marketplace.name() + ":" + group + ":" + accountId;
  }

  private static int nanosToSecondsCeil(long nanos) {
    if (nanos <= 0L) {
      return 1;
    }
    double seconds = nanos / (double) NANOS_IN_SECOND;
    long rounded = (long) Math.ceil(seconds);
    return (int) Math.max(1L, rounded);
  }

  private record BucketState(long lastRefillNanos, double availableTokens) {
  }
}
