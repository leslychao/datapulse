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
  public final Retry retryFor(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      RetryPolicy cfg
  ) {
    final int maxAttempts = cfg.getMaxAttempts();
    final Duration base = cfg.getBaseBackoff();
    final Duration cap = cfg.getMaxBackoff();

    return Retry.from(signals -> signals.flatMap(retrySignal -> {
      long attempt = retrySignal.totalRetries() + 1;
      Throwable error = retrySignal.failure();
      boolean exhausted = retrySignal.totalRetries() >= maxAttempts - 1;

      if (error instanceof WebClientResponseException ex) {
        int status = ex.getStatusCode().value();
        HttpHeaders headers = ex.getHeaders();

        if (isRetryableStatus(status)) {
          Duration effectiveDelay = computeEffectiveDelay(
              retrySignal.totalRetries(),
              headers,
              status,
              base,
              cap
          );
          Duration backoff = nn(effectiveDelay);

          if (status == STATUS_TOO_MANY_REQUESTS) {
            if (exhausted || isTooLongForInMemoryBackoff(marketplace, endpoint, backoff, cfg)) {
              TooManyRequestsBackoffRequiredException delegated =
                  createTooManyRequestsBackoffException(marketplace, endpoint, backoff);

              log.warn(
                  "[{}:{}] 429 retry {} → delegate to external backoff ({}s)",
                  marketplace,
                  endpoint,
                  exhausted ? "exhausted" : "too long",
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
              "[{}:{}] retry #{} in {} (status={}, headerDelay={})",
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
        Duration backoff = nn(expBackoff(retrySignal.totalRetries(), base, cap));

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

  protected TooManyRequestsBackoffRequiredException createTooManyRequestsBackoffException(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      Duration backoff
  ) {
    int seconds = (int) Math.max(0L, backoff.getSeconds());
    String message = "429 backoff required: marketplace=%s endpoint=%s delay=%ss"
        .formatted(marketplace, endpoint, seconds);

    return new TooManyRequestsBackoffRequiredException(
        marketplace,
        endpoint,
        seconds,
        message
    );
  }
}
