package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties.RetryPolicy;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
  private static final long MAX_BACKOFF_EXPONENT = 4L;

  protected static final int STATUS_REQUEST_TIMEOUT = 408;
  protected static final int STATUS_TOO_EARLY = 425;
  protected static final int STATUS_TOO_MANY_REQUESTS = 429;
  protected static final int STATUS_SERVICE_UNAVAILABLE = 503;

  protected static final String HDR_X_RETRY = "X-Ratelimit-Retry";

  @Override
  public final Retry retryFor(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      RetryPolicy cfg
  ) {
    final int maxAttempts = cfg.getMaxAttempts();
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException(
          "RetryPolicy.maxAttempts must be > 0 (marketplace=%s, endpoint=%s, value=%d)"
              .formatted(marketplace, endpoint, maxAttempts)
      );
    }

    final Duration base = cfg.getBaseBackoff();
    final Duration cap = cfg.getMaxBackoff();

    if (base == null || cap == null) {
      throw new IllegalArgumentException(
          "RetryPolicy.baseBackoff and maxBackoff must be non-null "
              + "(marketplace=%s, endpoint=%s)".formatted(marketplace, endpoint)
      );
    }

    return Retry.from(signals -> signals.flatMap(retrySignal -> {
      long retriesSoFar = retrySignal.totalRetries();
      long attempt = retriesSoFar + 1;
      boolean exhausted = retriesSoFar >= maxAttempts - 1;
      Throwable error = retrySignal.failure();

      if (error instanceof WebClientResponseException ex) {
        int status = ex.getStatusCode().value();
        HttpHeaders headers = ex.getHeaders();

        if (isRetryableStatus(status)) {
          Duration effectiveDelay = computeEffectiveDelay(
              marketplace,
              endpoint,
              retriesSoFar,
              headers,
              status,
              base,
              cap
          );
          Duration backoff = sanitizeDelay(effectiveDelay);

          if (status == STATUS_TOO_MANY_REQUESTS) {
            if (isTooLongForInMemoryBackoff(marketplace, endpoint, backoff, cfg)) {
              TooManyRequestsBackoffRequiredException delegated =
                  createTooManyRequestsBackoffException(marketplace, endpoint, backoff);

              log.warn(
                  "[{}:{}] 429 retry delegated to external backoff: delay={}s exceeds in-memory limit={}s",
                  marketplace,
                  endpoint,
                  backoff.getSeconds(),
                  maxInMemory429Backoff(marketplace, endpoint, cfg).getSeconds()
              );

              return Mono.error(delegated);
            }

            if (exhausted) {
              TooManyRequestsBackoffRequiredException delegated =
                  createTooManyRequestsBackoffException(marketplace, endpoint, backoff);

              log.warn(
                  "[{}:{}] 429 retry exhausted after {} attempts; delegating to external backoff with delay={}s",
                  marketplace,
                  endpoint,
                  maxAttempts,
                  backoff.getSeconds()
              );

              return Mono.error(delegated);
            }
          }

          if (exhausted) {
            log.warn(
                "[{}:{}] retry exhausted after {} attempts; lastStatus={}",
                marketplace,
                endpoint,
                maxAttempts,
                status
            );
            return Mono.error(error);
          }

          log.info(
              "[{}:{}] retry #{} in {} (status={}, effectiveDelay={})",
              marketplace,
              endpoint,
              attempt,
              backoff,
              status,
              effectiveDelay
          );

          return Mono.delay(backoff);
        }

        if (exhausted) {
          log.warn(
              "[{}:{}] non-retryable status {}; giving up after {} attempts",
              marketplace,
              endpoint,
              status,
              maxAttempts
          );
        }

        return Mono.error(error);
      }

      if (error instanceof TimeoutException) {
        Duration backoff = sanitizeDelay(expBackoff(retriesSoFar, base, cap));

        if (exhausted) {
          log.warn(
              "[{}:{}] timeout retry exhausted after {} attempts",
              marketplace,
              endpoint,
              maxAttempts
          );
          return Mono.error(error);
        }

        log.info(
            "[{}:{}] timeout → retry #{} in {}",
            marketplace,
            endpoint,
            attempt,
            backoff
        );
        return Mono.delay(backoff);
      }

      if (exhausted) {
        log.warn(
            "[{}:{}] retry exhausted after {} attempts; cause={}",
            marketplace,
            endpoint,
            maxAttempts,
            error.getClass().getSimpleName()
        );
      }

      return Mono.error(error);
    }));
  }

  protected Duration maxInMemory429Backoff(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      RetryPolicy cfg
  ) {
    return Duration.ofSeconds(2);
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

  protected Duration computeEffectiveDelay(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      long retries,
      HttpHeaders headers,
      int status,
      Duration base,
      Duration cap
  ) {
    Duration headerDelay = computeHeaderDelay(headers, status);
    if (headerDelay != null && !headerDelay.isNegative()) {
      return headerDelay;
    }
    return expBackoff(retries, base, cap);
  }

  protected Duration computeHeaderDelay(HttpHeaders headers, int status) {
    Duration d1 = parseSeconds(headers.getFirst(HDR_X_RETRY));
    if (d1 != null && !d1.isNegative()) {
      return d1;
    }

    Duration d2 = parseRetryAfter(headers);
    if (d2 != null && !d2.isNegative()) {
      return d2;
    }

    return null;
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
    String trimmed = Optional.ofNullable(value)
        .map(String::trim)
        .orElse("");

    if (trimmed.isEmpty()) {
      return null;
    }

    if (trimmed.chars().allMatch(Character::isDigit)) {
      return Optional.of(trimmed)
          .map(Long::parseLong)
          .filter(v -> v >= 0)
          .map(Duration::ofSeconds)
          .orElse(null);
    }

    try {
      ZonedDateTime retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
      long seconds = Duration.between(Instant.now(), retryAt.toInstant()).getSeconds();
      if (seconds <= 0) {
        return Duration.ZERO;
      }
      return Duration.ofSeconds(seconds);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  protected static Duration expBackoff(long retries, Duration base, Duration cap) {
    long attempt = Math.max(1L, retries + 1L);
    long baseMillis = base.toMillis();
    long capMillis = cap.toMillis();

    if (baseMillis <= 0L || capMillis <= 0L) {
      return Duration.ZERO;
    }

    long exponent = Math.min(attempt - 1L, MAX_BACKOFF_EXPONENT);
    long multiplier = 1L << exponent;

    long raw;
    if (baseMillis > Long.MAX_VALUE / multiplier) {
      raw = capMillis;
    } else {
      raw = baseMillis * multiplier;
    }

    long capped = Math.min(raw, capMillis);

    long jitterBound = (long) (capped * JITTER_FACTOR);
    long jitter = jitterBound > 0L
        ? (long) (Math.random() * jitterBound)
        : 0L;

    long total = Math.min(capMillis, capped + jitter);
    return Duration.ofMillis(total);
  }

  protected static Duration sanitizeDelay(Duration delay) {
    if (delay == null || delay.isNegative()) {
      return Duration.ZERO;
    }
    return delay;
  }

  protected TooManyRequestsBackoffRequiredException createTooManyRequestsBackoffException(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      Duration backoff
  ) {
    int seconds = (int) Math.max(0L, backoff.getSeconds());
    return new TooManyRequestsBackoffRequiredException(
        marketplace,
        endpoint,
        seconds,
        "429 backoff required: marketplace=%s endpoint=%s delay=%ss"
            .formatted(marketplace, endpoint, seconds)
    );
  }
}
