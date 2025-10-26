package io.datapulse.core.resilience;

import io.datapulse.core.client.RetryAfterSupport;
import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.domain.MarketplaceType;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.Retry.RetrySignal;

@Component
@RequiredArgsConstructor
public class ResilienceFactory {

  private final MarketplaceProperties props;

  public RateLimiter rateLimiter(MarketplaceType marketplaceType) {
    var resilience = props.getProviders().get(marketplaceType).getResilience();
    RateLimiterConfig cfg = RateLimiterConfig.custom()
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .limitForPeriod(resilience.getLimitForPeriod())
        .timeoutDuration(Duration.ofSeconds(2))
        .build();
    return RateLimiter.of(marketplaceType + ".rl", cfg);
  }

  public Bulkhead bulkhead(MarketplaceType marketplaceType) {
    var resilience = props.getProviders().get(marketplaceType).getResilience();
    BulkheadConfig cfg = BulkheadConfig.custom()
        .maxConcurrentCalls(resilience.getMaxConcurrentCalls())
        .maxWaitDuration(Duration.ofSeconds(1))
        .build();
    return Bulkhead.of(marketplaceType + ".bh", cfg);
  }

  public Retry retry(MarketplaceType marketplaceType) {
    var resilience = props.getProviders().get(marketplaceType).getResilience();

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

          if (failure instanceof WebClientResponseException wcre) {
            int code = wcre.getStatusCode().value();

            if (!(code == 429 || code >= 500 || code == 408 || code == 425)) {
              return Mono.error(failure);
            }

            if (code == 429) {
              Duration d = RetryAfterSupport.parse(wcre.getHeaders(), Clock.systemUTC(),
                  retryAfterFallback);
              return Mono.delay(d).then();
            }

            return Mono.delay(expBackoffWithJitter(rs.totalRetriesInARow(), baseBackoff, maxJitter))
                .then();
          }

          if (failure instanceof WebClientRequestException) {
            return Mono.delay(expBackoffWithJitter(rs.totalRetriesInARow(), baseBackoff, maxJitter))
                .then();
          }

          return Mono.error(failure);
        })
    );
  }

  private static Duration expBackoffWithJitter(long retriesInARow, Duration baseBackoff,
      Duration maxJitter) {
    long attempt = Math.max(1, retriesInARow + 1);
    Duration exp = baseBackoff.multipliedBy((long) Math.pow(2, attempt - 1));
    if (maxJitter.isZero()) {
      return exp;
    }
    long jitterMs = ThreadLocalRandom.current().nextLong(0, maxJitter.toMillis() + 1);
    return exp.plus(Duration.ofMillis(jitterMs));
  }
}
