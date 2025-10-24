package io.datapulse.core.resilience;

import io.datapulse.core.client.RetryAfterSupport;
import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.core.exception.HttpErrorException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.Retry.RetrySignal;

@Component
@RequiredArgsConstructor
public class ResilienceFactory {

  private final MarketplaceProperties props;

  public RateLimiter rateLimiter(String providerKey) {
    var resilience = props.getProviders().get(providerKey).getResilience();
    RateLimiterConfig cfg = RateLimiterConfig.custom()
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .limitForPeriod(resilience.getLimitForPeriod())
        .timeoutDuration(Duration.ofSeconds(2))
        .build();
    return RateLimiter.of(providerKey + ".rl", cfg);
  }

  public Bulkhead bulkhead(String providerKey) {
    var resilience = props.getProviders().get(providerKey).getResilience();
    BulkheadConfig cfg = BulkheadConfig.custom()
        .maxConcurrentCalls(resilience.getMaxConcurrentCalls())
        .maxWaitDuration(Duration.ofSeconds(1))
        .build();
    return Bulkhead.of(providerKey + ".bh", cfg);
  }

  public Retry retry(String providerKey) {
    var resilience = props.getProviders().get(providerKey).getResilience();

    final int maxAttempts = resilience.getMaxAttempts();
    final Duration baseBackoff = resilience.getBaseBackoff();
    final Duration maxJitter =
        resilience.getMaxJitter() == null ? Duration.ZERO : resilience.getMaxJitter();
    final Duration retryAfterFallback =
        resilience.getRetryAfterFallback() == null ? Duration.ZERO
            : resilience.getRetryAfterFallback();

    return Retry.from(retrySignals ->
        retrySignals.flatMap((RetrySignal rs) -> {
          Throwable failure = rs.failure();

          if (rs.totalRetries() >= (maxAttempts - 1)) {
            return Mono.error(failure);
          }

          if (failure instanceof HttpErrorException he) {
            int statusCode = he.getStatusCode().value();
            if (!(statusCode == 429 || statusCode >= 500 || statusCode == 408
                || statusCode == 425)) {
              return Mono.error(failure);
            }

            if (statusCode == 429) {
              Duration d = RetryAfterSupport.parse(he.getHeaders(), Clock.systemUTC(),
                  retryAfterFallback);
              return Mono.delay(d).then();
            }
          }

          long attempt = Math.max(1, rs.totalRetriesInARow() + 1);
          Duration exp = baseBackoff.multipliedBy((long) Math.pow(2, attempt - 1));
          Duration jitter = maxJitter.isZero()
              ? Duration.ZERO
              : Duration.ofMillis(
                  ThreadLocalRandom.current().nextLong(0, maxJitter.toMillis() + 1));

          return Mono.delay(exp.plus(jitter)).then();
        })
    );
  }
}
