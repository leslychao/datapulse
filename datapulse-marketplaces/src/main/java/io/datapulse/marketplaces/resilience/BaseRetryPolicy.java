package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties.RetryPolicy;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
public abstract class BaseRetryPolicy implements MarketplaceRetryPolicy {

  protected static final double JITTER_FACTOR = 0.20;

  protected static final int STATUS_REQUEST_TIMEOUT = 408;
  protected static final int STATUS_TOO_EARLY = 425;
  protected static final int STATUS_TOO_MANY_REQUESTS = 429;
  protected static final int STATUS_SERVICE_UNAVAILABLE = 503;

  protected static final String HDR_X_RETRY = "X-Ratelimit-Retry";

  @Override
  public final Retry retryFor(MarketplaceType marketplace, EndpointKey endpoint, RetryPolicy cfg) {
    final int maxAttempts = cfg.getMaxAttempts();
    final Duration base = cfg.getBaseBackoff();
    final Duration cap = cfg.getMaxBackoff();

    return Retry.from(signals -> signals.flatMap(rs -> {
      final long attempt = rs.totalRetries() + 1;
      final Throwable error = rs.failure();

      if (rs.totalRetries() >= maxAttempts - 1) {
        log.warn("[{}:{}] retry exhausted after {} attempts; cause={}",
            marketplace, endpoint, maxAttempts, error.getClass().getSimpleName());
        return Mono.error(error);
      }

      if (error instanceof WebClientResponseException ex) {
        final int status = ex.getStatusCode().value();
        final HttpHeaders headers = ex.getHeaders();

        if (isRetryableStatus(status)) {
          Duration headerDelay = computeHeaderDelay(headers, status);
          Duration delay = (headerDelay != null && !headerDelay.isNegative())
              ? headerDelay
              : expBackoff(rs.totalRetries(), base, cap);

          Duration d = nn(delay);

          if (status == STATUS_TOO_MANY_REQUESTS
              && isTooLongForInMemoryBackoff(marketplace, endpoint, d, cfg)) {

            int seconds = (int) Math.max(0L, d.getSeconds());
            log.warn(
                "[{}:{}] long 429 backoff={}s detected → delegate to external backoff (Rabbit/wait-queue)",
                marketplace, endpoint, seconds
            );

            String message = "Long 429 backoff required: marketplace=%s endpoint=%s delay=%ss"
                .formatted(marketplace, endpoint, seconds);

            return Mono.error(
                new TooManyRequestsBackoffRequiredException(
                    marketplace,
                    endpoint,
                    seconds,
                    message
                )
            );
          }

          log.info("[{}:{}] retry #{} in {} (status={}, headerDelay={})",
              marketplace, endpoint, attempt, d, status, headerDelay);
          return Mono.delay(d);
        }

        return Mono.error(error);
      }

      if (error instanceof TimeoutException) {
        Duration d = nn(expBackoff(rs.totalRetries(), base, cap));
        log.info("[{}:{}] timeout → retry #{} in {}", marketplace, endpoint, attempt, d);
        return Mono.delay(d);
      }

      return Mono.error(error);
    }));
  }

  protected Duration maxInMemory429Backoff(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      RetryPolicy cfg
  ) {
    return Duration.ofSeconds(10);
  }

  protected boolean isTooLongForInMemoryBackoff(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      Duration delay,
      RetryPolicy cfg
  ) {
    Duration limit = maxInMemory429Backoff(marketplace, endpoint, cfg);
    return delay != null && limit != null && delay.compareTo(limit) > 0;
  }

  protected Duration computeHeaderDelay(HttpHeaders headers, int status) {
    return parseRetryAfter(headers);
  }

  protected static boolean isRetryableStatus(int status) {
    return status == STATUS_REQUEST_TIMEOUT
        || status == STATUS_TOO_EARLY
        || status == STATUS_TOO_MANY_REQUESTS
        || (status >= 500 && status <= 599);
  }

  protected static Duration parseRetryAfter(HttpHeaders headers) {
    return parseSeconds(headers.getFirst(HttpHeaders.RETRY_AFTER));
  }

  protected static Duration parseSeconds(String value) {
    return Optional.ofNullable(value)
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .filter(v -> v.chars().allMatch(Character::isDigit))
        .map(Long::parseLong)
        .filter(v -> v >= 0)
        .map(Duration::ofSeconds)
        .orElse(null);
  }

  protected static Duration expBackoff(long retries, Duration base, Duration cap) {
    long attempt = Math.max(1, retries + 1);
    long raw = base.toMillis() * (1L << Math.min(attempt - 1, 4));
    long capped = Math.min(raw, cap.toMillis());
    long jitter = Math.round(capped * JITTER_FACTOR * Math.random());
    long total = Math.min(cap.toMillis(), capped + jitter);
    return Duration.ofMillis(total);
  }

  protected static Duration nn(Duration d) {
    return (d == null || d.isNegative()) ? Duration.ZERO : d;
  }
}
