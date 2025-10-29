package io.datapulse.marketplaces.resilience;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResilienceManager {

  private static final Duration MAX_CAP = Duration.ofMinutes(5);

  private final MarketplaceProperties props;

  private final Cache<String, RateLimiter> rlCache = Caffeine.newBuilder()
      .maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();
  private final Cache<String, Bulkhead> bhCache = Caffeine.newBuilder()
      .maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();
  private final Cache<String, Retry> retryCache = Caffeine.newBuilder()
      .maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

  /* ====== Public API ====== */

  public <T> Flux<T> apply(Flux<T> raw, MarketplaceType type, String endpointKey, long accountId) {
    return doApply(raw, type, endpointKey, "acc:" + accountId);
  }

  /**
   * Перегрузка, если нужно шарить не per-account, а per-credential (по произвольному ключу).
   */
  public <T> Flux<T> applyWithLimiterKey(Flux<T> raw, MarketplaceType type, String endpointKey,
      String limiterKey) {
    return doApply(raw, type, endpointKey, limiterKey);
  }

  /* ====== Core impl ====== */

  private <T> Flux<T> doApply(Flux<T> raw, MarketplaceType type, String endpointKey,
      String limiterKey) {
    var epKit = doBuildKit(type, endpointKey, limiterKey);
    var globalOpt = tryBuildGlobalKit(type, limiterKey);

    Flux<T> withGuards = globalOpt
        .map(gk -> apply(apply(raw, gk), epKit)) // GLOBAL → PER-ENDPOINT
        .orElseGet(() -> apply(raw, epKit));

    // Ретраи — пер-эндпоинт (контекст логов и политики привязаны к конкретному API)
    return withGuards.retryWhen(epKit.retry());
  }

  /**
   * Пытается построить GLOBAL-kit на основе базовой resilience-конфигурации провайдера. Если base
   * == null (битая конфигурация) — возвращает empty.
   */
  private Optional<ResilienceKit> tryBuildGlobalKit(MarketplaceType type, String limiterKey) {
    MarketplaceProperties.Provider provider = props.get(type);
    MarketplaceProperties.Resilience base = provider.getResilience();
    if (base == null) {
      return Optional.empty();
    }
    return Optional.of(doBuildKit(type, null, limiterKey));
  }

  /**
   * Строим комплект для (type, endpointKey, limiterKey).
   */
  private ResilienceKit doBuildKit(MarketplaceType type, String endpointKey, String limiterKey) {
    var r = effectiveResilience(type, endpointKey);

    // Валидации / нормализация
    final int maxAttempts = reqPos(r.getMaxAttempts(), "maxAttempts");
    final Duration base = reqPositive(r.getBaseBackoff(), "baseBackoff");
    final Duration cap = reqCap(r.getMaxBackoff(), "maxBackoff");
    final Duration jitter = nonNegative(orZero(r.getMaxJitter()), "maxJitter");
    final Duration raFallback = reqCap(orZero(r.getRetryAfterFallback()), "retryAfterFallback");
    if (base.compareTo(cap) > 0) {
      throw new IllegalArgumentException("baseBackoff must be <= maxBackoff");
    }
    final Duration tokenWait = reqCap(
        Objects.requireNonNull(r.getTokenWaitTimeout(), "tokenWaitTimeout"), "tokenWaitTimeout");
    final Duration bhWait = reqCap(
        Objects.requireNonNull(r.getBulkheadWait(), "bulkheadWait"), "bulkheadWait");

    var rlCfg = RateLimiterConfig.custom()
        .limitRefreshPeriod(Objects.requireNonNull(r.getLimitRefreshPeriod(), "limitRefreshPeriod"))
        .limitForPeriod(reqPos(r.getLimitForPeriod(), "limitForPeriod"))
        .timeoutDuration(tokenWait)
        .build();

    var bhCfg = BulkheadConfig.custom()
        .maxConcurrentCalls(reqPos(r.getMaxConcurrentCalls(), "maxConcurrentCalls"))
        .maxWaitDuration(bhWait)
        .build();

    String ep = epLabel(endpointKey);
    String rlName = type + "." + ep + ".rl." + limiterKey;
    String bhName = type + "." + ep + ".bh." + limiterKey;
    String rtName = type + "." + ep + ".rt." + limiterKey;
    String ctx = type + ":" + ep + ":" + limiterKey;

    RateLimiter rl = rlCache.get(rlName, n -> RateLimiter.of(n, rlCfg));
    Bulkhead bh = bhCache.get(bhName, n -> Bulkhead.of(n, bhCfg));
    Retry rt = retryCache.get(rtName,
        n -> buildRetry(maxAttempts, base, cap, jitter, raFallback, ctx));

    return new ResilienceKit(rl, bh, rt);
  }

  /**
   * Комплект ограничителей и Retry.
   */
  public record ResilienceKit(RateLimiter rl, Bulkhead bh, Retry retry) {

  }

  /* ====== Локальные операторы RL/BH ====== */

  public <T> Flux<T> apply(Flux<T> src, ResilienceKit k) {
    return applyBulkhead(applyRateLimiter(src, k.rl()), k.bh());
  }

  private static <T> Flux<T> applyRateLimiter(Flux<T> src, RateLimiter limiter) {
    return Flux.defer(() -> {
      long waitNanos = limiter.reservePermission();
      if (waitNanos < 0) {
        return Flux.error(RequestNotPermitted.createRequestNotPermitted(limiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? src : src.delaySubscription(wait);
    });
  }

  private static <T> Mono<T> applyRateLimiter(Mono<T> src, RateLimiter limiter) {
    return Mono.defer(() -> {
      long waitNanos = limiter.reservePermission();
      if (waitNanos < 0) {
        return Mono.error(RequestNotPermitted.createRequestNotPermitted(limiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? src : src.delaySubscription(wait);
    });
  }

  private static <T> Flux<T> applyBulkhead(Flux<T> src, Bulkhead bh) {
    return Flux.defer(() -> {
      if (!bh.tryAcquirePermission()) {
        return Flux.error(BulkheadFullException.createBulkheadFullException(bh));
      }
      return src.doFinally(sig -> bh.onComplete());
    });
  }

  private static <T> Mono<T> applyBulkhead(Mono<T> src, Bulkhead bh) {
    return Mono.defer(() -> {
      if (!bh.tryAcquirePermission()) {
        return Mono.error(BulkheadFullException.createBulkheadFullException(bh));
      }
      return src.doFinally(sig -> bh.onComplete());
    });
  }

  /* ====== Retry ====== */

  private Retry buildRetry(int maxAttempts, Duration base, Duration cap, Duration jitter,
      Duration raFallback, String ctx) {
    return Retry.from(signals -> signals.flatMap(rs -> {
      final Throwable t = rs.failure();
      final long attempt = rs.totalRetries() + 1;

      if (rs.totalRetries() >= maxAttempts - 1) {
        log.warn("[{}] retry exhausted after {} attempts, last-cause={}", ctx, maxAttempts,
            simpleName(t));
        return Mono.error(t);
      }

      if (t instanceof WebClientResponseException w) {
        final int code = w.getStatusCode().value();
        if (code == 429 || code == 503) {
          Duration ra = RetryAfterSupport.parse(w.getHeaders(), raFallback);
          Duration delay = BackoffSupport.addJitterAndCap(ra, jitter, cap);
          log.info("[{}] {} -> retry (attempt #{}) in {} (headers={}, jitter={}, cap={})",
              ctx, code, attempt, delay, shortRetryAfterHeaders(w), jitter, cap);
          return Mono.delay(delay);
        }
        if (code == 409 || code == 408 || code == 425 || (code >= 500 && code <= 599)) {
          Duration delay = BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter);
          log.info("[{}] {} -> retry (attempt #{}) in {} (base={}, jitter={}, cap={})",
              ctx, code, attempt, delay, base, jitter, cap);
          return Mono.delay(delay);
        }
        log.warn("[{}] non-retryable status={}, giving up (attempt #{})", ctx, code, attempt);
        return Mono.error(t);
      }

      if (t instanceof WebClientRequestException req && isTransientNetwork(req)) {
        Duration delay = BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter);
        log.info("[{}] transient network -> retry (attempt #{}) in {} (base={}, jitter={}, cap={})",
            ctx, attempt, delay, base, jitter, cap);
        return Mono.delay(delay);
      }

      if (t instanceof RequestNotPermitted || t instanceof BulkheadFullException) {
        Duration delay = BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter);
        log.info("[{}] RL/BH blocked -> retry (attempt #{}) in {} (base={}, jitter={}, cap={})",
            ctx, attempt, delay, base, jitter, cap);
        return Mono.delay(delay);
      }

      log.warn("[{}] non-retryable exception={}, giving up (attempt #{})", ctx, simpleName(t),
          attempt);
      return Mono.error(t);
    }));
  }

  /* ====== Конфиг и хелперы ====== */

  private MarketplaceProperties.Resilience effectiveResilience(MarketplaceType type,
      String endpointKey) {
    var provider = props.get(type); // AppException, если провайдера нет
    var overrides = provider.getResilienceOverrides();

    // 1) сначала ищем endpoint override
    if (overrides != null) {
      var local = overrides.get(endpointKey);
      if (local != null) {
        return local;
      }
    }

    // 2) если endpoint override нет — используем базовую, НО если и её нет → это ошибка конфигурации
    var base = provider.getResilience();
    if (base == null) {
      throw new IllegalStateException(
          "Missing resilience configuration for provider " + type
              + " (neither base nor override defined for endpoint=" + endpointKey + ")");
    }
    return base;
  }

  private static Duration orZero(Duration d) {
    return d == null ? Duration.ZERO : d;
  }

  private static int reqPos(Integer v, String name) {
    Objects.requireNonNull(v, name);
    if (v <= 0) {
      throw new IllegalArgumentException(name + " must be > 0");
    }
    return v;
  }

  private static Duration reqCap(Duration d, String name) {
    Objects.requireNonNull(d, name);
    if (d.isNegative() || d.compareTo(MAX_CAP) > 0) {
      throw new IllegalArgumentException(name + " must be in [0.." + MAX_CAP.toMinutes() + "m]");
    }
    return d;
  }

  private static Duration reqPositive(Duration d, String name) {
    Objects.requireNonNull(d, name);
    if (d.isZero() || d.isNegative()) {
      throw new IllegalArgumentException(name + " must be > 0");
    }
    return d;
  }

  private static Duration nonNegative(Duration d, String name) {
    Objects.requireNonNull(d, name);
    if (d.isNegative()) {
      throw new IllegalArgumentException(name + " must be >= 0");
    }
    return d;
  }

  private static boolean isTransientNetwork(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof SocketTimeoutException
          || c instanceof ConnectException
          || c instanceof NoRouteToHostException
          || c instanceof UnknownHostException
          || c instanceof SSLException
          || c instanceof ClosedChannelException
          || c instanceof PrematureCloseException) {
        return true;
      }
    }
    return false;
  }

  private static String simpleName(Throwable t) {
    return (t == null) ? "null" : t.getClass().getSimpleName();
  }

  private static String shortRetryAfterHeaders(WebClientResponseException w) {
    var h = w.getHeaders();
    String ra = h.getFirst("Retry-After");
    String xr = h.getFirst("X-Ratelimit-Retry");
    String xrst = h.getFirst("X-Ratelimit-Reset");
    return "retry-after=" + (ra == null ? "-" : ra)
        + ", x-ratelimit-retry=" + (xr == null ? "-" : xr)
        + ", x-ratelimit-reset=" + (xrst == null ? "-" : xrst);
  }

  private static String epLabel(String endpointKey) {
    return (endpointKey == null || endpointKey.isBlank()) ? "GLOBAL" : endpointKey;
  }
}
