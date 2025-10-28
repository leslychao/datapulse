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
import java.util.Objects;
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
  private final Clock clock;

  public RateLimiter rateLimiter(MarketplaceType type) {
    var resilience = props.get(type).getResilience();
    var cfg = RateLimiterConfig.custom()
        .limitRefreshPeriod(
            requireNonNull(resilience.getLimitRefreshPeriod(), "limitRefreshPeriod"))
        .limitForPeriod(requirePositive(resilience.getLimitForPeriod(), "limitForPeriod"))
        .timeoutDuration(requireNonNull(resilience.getTokenWaitTimeout(), "tokenWaitTimeout"))
        .build();
    return RateLimiter.of(type + ".rl", cfg);
  }

  public Bulkhead bulkhead(MarketplaceType type) {
    var resilience = props.get(type).getResilience();
    var cfg = BulkheadConfig.custom()
        .maxConcurrentCalls(
            requirePositive(resilience.getMaxConcurrentCalls(), "maxConcurrentCalls"))
        .maxWaitDuration(requireNonNull(resilience.getBulkheadWait(), "bulkheadWait"))
        .build();
    return Bulkhead.of(type + ".bh", cfg);
  }

  public Retry retry(MarketplaceType type) {
    var r = props.get(type).getResilience();

    final int maxAttempts = requirePositive(r.getMaxAttempts(), "maxAttempts");
    final Duration baseBackoff = requireNonNull(r.getBaseBackoff(), "baseBackoff");
    final Duration maxBackoff = requireNonNull(r.getMaxBackoff(), "maxBackoff");
    final Duration maxJitter = nonNullOrZero(r.getMaxJitter());
    final Duration retryAfterFallback = nonNullOrZero(r.getRetryAfterFallback());

    return Retry.from(signals -> signals.flatMap((RetrySignal rs) -> {
      final Throwable failure = rs.failure();

      // исчерпали попытки
      if (rs.totalRetries() >= (maxAttempts - 1)) {
        return Mono.error(failure);
      }

      // HTTP-ответ
      if (failure instanceof WebClientResponseException wcre) {
        final int code = wcre.getStatusCode().value();

        if (!isRetriableStatus(code)) {
          return Mono.error(failure);
        }

        // Уважаем Retry-After для 429 и 503 (RFC 9110)
        if (code == 429 || code == 503) {
          Duration d = RetryAfterSupport.parse(wcre.getHeaders(), clock, retryAfterFallback);
          return Mono.delay(nonNegative(d)).then();
        }

        Duration delay = expBackoffWithJitterCapped(rs.totalRetriesInARow(), baseBackoff,
            maxBackoff, maxJitter);
        return Mono.delay(delay).then();
      }

      // Клиентские сетевые сбои — ретраим по экспоненте
      if (failure instanceof WebClientRequestException) {
        Duration delay = expBackoffWithJitterCapped(rs.totalRetriesInARow(), baseBackoff,
            maxBackoff, maxJitter);
        return Mono.delay(delay).then();
      }

      // Остальное — не ретраим
      return Mono.error(failure);
    }));
  }

  // ===== helpers =====

  private static boolean isRetriableStatus(int code) {
    // 408 Request Timeout, 425 Too Early, 429 Too Many Requests, 5xx
    return code == 408 || code == 425 || code == 429 || (code >= 500 && code <= 599);
  }

  private static Duration expBackoffWithJitterCapped(long retriesInARow,
      Duration baseBackoff,
      Duration maxBackoff,
      Duration maxJitter) {
    long attempt = Math.max(1, retriesInARow + 1);

    // factor = 2^(attempt-1) без double; защитимся от больших сдвигов
    long factor = (attempt >= 63) ? Long.MAX_VALUE : (1L << (attempt - 1));

    Duration exp;
    try {
      exp = baseBackoff.multipliedBy(factor);
    } catch (ArithmeticException overflow) {
      exp = Duration.ofMillis(Long.MAX_VALUE);
    }

    if (exp.compareTo(maxBackoff) > 0) {
      exp = maxBackoff;
    }

    if (maxJitter.isZero() || maxJitter.isNegative()) {
      return exp;
    }

    long jitterBoundMs = Math.max(0, maxJitter.toMillis());
    long jitterMs =
        (jitterBoundMs == 0) ? 0 : ThreadLocalRandom.current().nextLong(0, jitterBoundMs + 1);

    return safePlus(exp, Duration.ofMillis(jitterMs), maxBackoff);
  }

  private static Duration safePlus(Duration base, Duration add, Duration cap) {
    Duration sum;
    try {
      sum = base.plus(add);
    } catch (ArithmeticException overflow) {
      sum = Duration.ofMillis(Long.MAX_VALUE);
    }
    return (sum.compareTo(cap) > 0) ? cap : sum;
  }

  private static int requirePositive(Integer v, String name) {
    Objects.requireNonNull(v, name + " must not be null");
    if (v <= 0) {
      throw new IllegalArgumentException(name + " must be > 0");
    }
    return v;
  }

  private static <T> T requireNonNull(T v, String name) {
    return Objects.requireNonNull(v, name + " must not be null");
  }

  private static Duration nonNullOrZero(Duration v) {
    return (v == null) ? Duration.ZERO : v;
  }

  private static Duration nonNegative(Duration d) {
    return d.isNegative() ? Duration.ZERO : d;
  }
}
