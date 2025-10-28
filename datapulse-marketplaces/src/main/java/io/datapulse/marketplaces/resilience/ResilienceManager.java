package io.datapulse.marketplaces.resilience;

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
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Единый фасад устойчивости: - создаёт/кэширует RateLimiter, Bulkhead, Retry - предоставляет
 * операторы для Mono/Flux (rate limit + bulkhead)
 */
@Component
@RequiredArgsConstructor
public class ResilienceManager {

  private final MarketplaceProperties props;

  private final RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
  private final BulkheadRegistry bhRegistry = BulkheadRegistry.ofDefaults();
  private final ConcurrentHashMap<String, Retry> retryCache = new ConcurrentHashMap<>();

  /**
   * Набор устойчивости для конкретного типа/эндпоинта/аккаунта.
   */
  public record ResilienceKit(RateLimiter rl, Bulkhead bh, Retry retry) {

  }

  /**
   * Построение комплекта.
   */
  public ResilienceKit kit(MarketplaceType type, String endpointKey, long accountId) {
    var r = effectiveResilience(type, endpointKey);

    var rlCfg = RateLimiterConfig.custom()
        .limitRefreshPeriod(Objects.requireNonNull(r.getLimitRefreshPeriod()))
        .limitForPeriod(reqPos(r.getLimitForPeriod()))
        .timeoutDuration(Objects.requireNonNull(r.getTokenWaitTimeout()))
        .build();

    var bhCfg = BulkheadConfig.custom()
        .maxConcurrentCalls(reqPos(r.getMaxConcurrentCalls()))
        .maxWaitDuration(Objects.requireNonNull(r.getBulkheadWait()))
        .build();

    String rlName = name(type, endpointKey, "rl", accountId);
    String bhName = name(type, endpointKey, "bh", accountId);
    String rtName = name(type, endpointKey, "rt", 0); // общий ретрай на эндпоинт

    RateLimiter rl = rlRegistry.rateLimiter(rlName, rlCfg);
    Bulkhead bh = bhRegistry.bulkhead(bhName, bhCfg);
    Retry rt = retryCache.computeIfAbsent(rtName, n -> buildRetry(r));

    return new ResilienceKit(rl, bh, rt);
  }

  /* ===================== Реактивные операторы ===================== */

  public <T> Flux<T> apply(Flux<T> source, ResilienceKit k) {
    return applyBulkhead(applyRateLimiter(source, k.rl()), k.bh());
  }

  public <T> Mono<T> apply(Mono<T> source, ResilienceKit k) {
    return applyBulkhead(applyRateLimiter(source, k.rl()), k.bh());
  }

  /* ===================== Приватные детали ===================== */

  private Retry buildRetry(MarketplaceProperties.Resilience r) {
    final int maxAttempts = reqPos(r.getMaxAttempts());
    final Duration base = Objects.requireNonNull(r.getBaseBackoff());
    final Duration cap = Objects.requireNonNull(r.getMaxBackoff());
    final Duration jitter = orZero(r.getMaxJitter());
    final Duration raFallback = orZero(r.getRetryAfterFallback());

    return Retry.from(signals -> signals.flatMap(rs -> {
      final Throwable t = rs.failure();

      // В Reactor maxAttempts включает первую попытку
      if (rs.totalRetries() >= maxAttempts - 1) {
        return Mono.error(t);
      }

      // HTTP-ответы
      if (t instanceof WebClientResponseException w) {
        final int code = w.getStatusCode().value();

        // 429 / 503 — уважаем Retry-After (и WB X-Ratelimit-*), либо fallback
        if (code == 429 || code == 503) {
          Duration d = RetryAfterSupport.parse(w.getHeaders(), raFallback);
          if (d == null || d.isNegative()) {
            d = Duration.ZERO;
          }
          return Mono.delay(BackoffSupport.addJitterAndCap(d, jitter, cap));
        }

        // Прочие ретраябельные статусы
        if (code == 409 || code == 408 || code == 425 || (code >= 500 && code <= 599)) {
          return Mono.delay(BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter));
        }

        // Остальное — не ретраим
        return Mono.error(t);
      }

      // Сетевые транзиенты
      if (t instanceof WebClientRequestException req && isTransientNetwork(req)) {
        return Mono.delay(BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter));
      }

      // Отказы лимитера / булкхеда — допустимо ретраить
      if (t instanceof RequestNotPermitted || t instanceof BulkheadFullException) {
        return Mono.delay(BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter));
      }

      // Всё остальное — не ретраим
      return Mono.error(t);
    }));
  }

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
      throw new IllegalArgumentException("значение должно быть > 0");
    }
    return v;
  }

  private static Duration orZero(Duration d) {
    return d == null ? Duration.ZERO : d;
  }

  private static boolean isTransientNetwork(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof SocketTimeoutException
          || c instanceof ConnectException
          || c instanceof NoRouteToHostException
          || c instanceof UnknownHostException
          || c instanceof javax.net.ssl.SSLException
          || c instanceof ClosedChannelException) {
        return true;
      }
    }
    return false;
  }

  /* ===== Локальные реализации rateLimiter/bulkhead операторов ===== */

  private static <T> Flux<T> applyRateLimiter(Flux<T> source, RateLimiter limiter) {
    return Flux.defer(() -> {
      long waitNanos = limiter.reservePermission();
      if (waitNanos < 0) {
        return Flux.error(RequestNotPermitted.createRequestNotPermitted(limiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? source : source.delaySubscription(wait);
    });
  }

  private static <T> Mono<T> applyRateLimiter(Mono<T> source, RateLimiter limiter) {
    return Mono.defer(() -> {
      long waitNanos = limiter.reservePermission();
      if (waitNanos < 0) {
        return Mono.error(RequestNotPermitted.createRequestNotPermitted(limiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? source : source.delaySubscription(wait);
    });
  }

  private static <T> Flux<T> applyBulkhead(Flux<T> source, Bulkhead bulkhead) {
    return Flux.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Flux.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      return source.doFinally(sig -> bulkhead.onComplete());
    });
  }

  private static <T> Mono<T> applyBulkhead(Mono<T> source, Bulkhead bulkhead) {
    return Mono.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Mono.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      return source.doFinally(sig -> bulkhead.onComplete());
    });
  }
}
