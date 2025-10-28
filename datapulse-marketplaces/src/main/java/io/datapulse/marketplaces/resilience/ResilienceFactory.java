package io.datapulse.marketplaces.resilience;

import io.datapulse.core.client.RetryAfterSupport;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class ResilienceFactory {

  private final MarketplaceProperties props;

  // Реестры для шаринга состояния лимитеров/булкхедов
  private final RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
  private final BulkheadRegistry bhRegistry = BulkheadRegistry.ofDefaults();

  // Кэш для кастомных реактивных Retry (reactor.util.retry.Retry)
  private final ConcurrentHashMap<String, Retry> retryCache = new ConcurrentHashMap<>();

  /* ===================== Public API ===================== */

  public RateLimiter rateLimiter(MarketplaceType type, String endpointKey, long accountId) {
    var r = effectiveResilience(type, endpointKey);
    var cfg = RateLimiterConfig.custom()
        .limitRefreshPeriod(Objects.requireNonNull(r.getLimitRefreshPeriod()))
        .limitForPeriod(reqPos(r.getLimitForPeriod()))
        .timeoutDuration(Objects.requireNonNull(r.getTokenWaitTimeout()))
        .build();

    String name = name(type, endpointKey, "rl", accountId);
    return rlRegistry.rateLimiter(name, cfg);
  }

  public Bulkhead bulkhead(MarketplaceType type, String endpointKey, long accountId) {
    var r = effectiveResilience(type, endpointKey);
    var cfg = BulkheadConfig.custom()
        .maxConcurrentCalls(reqPos(r.getMaxConcurrentCalls()))
        .maxWaitDuration(Objects.requireNonNull(r.getBulkheadWait()))
        .build();

    String name = name(type, endpointKey, "bh", accountId);
    return bhRegistry.bulkhead(name, cfg);
  }

  public Retry retry(MarketplaceType type, String endpointKey) {
    var r = effectiveResilience(type, endpointKey);

    final int maxAttempts = reqPos(r.getMaxAttempts());
    final Duration base = Objects.requireNonNull(r.getBaseBackoff());
    final Duration cap = Objects.requireNonNull(r.getMaxBackoff());
    final Duration jitter = orZero(r.getMaxJitter());
    final Duration raFallback = orZero(r.getRetryAfterFallback());

    String name = name(type, endpointKey, "rt", 0);

    // Кэшируем кастомный reactor Retry с нашей стратегией
    return retryCache.computeIfAbsent(name, n ->
        Retry.from(signals -> signals.flatMap(rs -> {
          final Throwable t = rs.failure();

          // досрочно выходим, если достигли предела
          if (rs.totalRetries() >= maxAttempts - 1) {
            return Mono.error(t);
          }

          // HTTP ответы
          if (t instanceof WebClientResponseException w) {
            final int code = w.getStatusCode().value();

            // 429 / 503 — уважаем Retry-After (и WB X-Ratelimit-*), либо fallback
            if (code == 429 || code == 503) {
              Duration d = RetryAfterSupport.parse(w.getHeaders(), raFallback);
              if (d == null || d.isNegative()) {
                d = Duration.ZERO;
              }
              return Mono.delay(d).then();
            }

            // 408 / 425 / 5xx — экспоненциальный бэкофф с джиттером
            if (code == 408 || code == 425 || (code >= 500 && code <= 599)) {
              return Mono.delay(backoff(rs.totalRetries(), base, cap, jitter)).then();
            }

            // Остальное — не ретраим
            return Mono.error(t);
          }

          // Сетевые транзиенты
          if (t instanceof WebClientRequestException req) {
            Throwable c = req.getCause();
            if (c instanceof SocketTimeoutException
                || c instanceof ConnectException
                || c instanceof NoRouteToHostException
                || (c != null && c.getClass().getName().contains("UnknownHostException"))) {
              return Mono.delay(backoff(rs.totalRetries(), base, cap, jitter)).then();
            }
          }

          // Отказы лимитера / булкхеда — можно/нужно ретраить
          if (t instanceof RequestNotPermitted || t instanceof BulkheadFullException) {
            return Mono.delay(backoff(rs.totalRetries(), base, cap, jitter)).then();
          }

          // Всё остальное — не ретраим
          return Mono.error(t);
        }))
    );
  }

  /* ===================== Helpers ===================== */

  private MarketplaceProperties.Resilience effectiveResilience(MarketplaceType type,
      String endpointKey) {
    var p = props.get(type);
    Map<String, MarketplaceProperties.Resilience> overrides = p.getResilienceOverrides();
    if (overrides != null) {
      var local = overrides.get(endpointKey);
      if (local != null) {
        return local;
      }
    }
    return p.getResilience();
  }

  private static String name(MarketplaceType type, String endpointKey, String kind,
      long accountId) {
    // пример: WILDBERRIES.reviews.rl.12345
    return type + "." + endpointKey + "." + kind + "." + accountId;
  }

  private static int reqPos(Integer v) {
    Objects.requireNonNull(v, "value");
    if (v <= 0) {
      throw new IllegalArgumentException("value must be > 0");
    }
    return v;
  }

  private static Duration orZero(Duration d) {
    return d == null ? Duration.ZERO : d;
  }

  /**
   * Экспоненциальный backoff с «ceil»-капом и add-джиттером. exp = min(cap, base * 2^(attempt-1)) +
   * rand(0..jitter)
   */
  private static Duration backoff(long retries, Duration base, Duration cap, Duration jitter) {
    long attempt = Math.max(1, retries + 1);
    long factor = attempt >= 63 ? Long.MAX_VALUE : (1L << (attempt - 1));

    Duration exp;
    try {
      exp = base.multipliedBy(factor);
    } catch (ArithmeticException e) {
      exp = Duration.ofMillis(Long.MAX_VALUE);
    }

    if (exp.compareTo(cap) > 0) {
      exp = cap;
    }
    if (jitter.isZero() || jitter.isNegative()) {
      return exp;
    }

    long bound = Math.max(0, jitter.toMillis());
    long rand = (bound == 0) ? 0 : ThreadLocalRandom.current().nextLong(bound + 1);

    try {
      Duration withJitter = exp.plusMillis(rand);
      return withJitter.compareTo(cap) > 0 ? cap : withJitter;
    } catch (ArithmeticException e) {
      return cap;
    }
  }
}
