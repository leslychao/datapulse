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
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Минимальный фасад: сборка RL/BH/Retry, применение к Mono/Flux.
 */
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
      .maximumSize(1000)
      .expireAfterAccess(Duration.ofHours(1))
      .build();

  public record ResilienceKit(RateLimiter rl, Bulkhead bh, Retry retry) {

  }

  public ResilienceKit kit(MarketplaceType type, String endpointKey, long accountId) {
    var r = effectiveResilience(type, endpointKey);

    // Валидация и нормализация параметров
    final int maxAttempts = reqPos(r.getMaxAttempts(), "maxAttempts");
    final Duration base = reqPositive(r.getBaseBackoff(), "baseBackoff");
    final Duration cap = reqCap(r.getMaxBackoff(), "maxBackoff");
    final Duration jitter = nonNegative(orZero(r.getMaxJitter()), "maxJitter");
    final Duration raFallback = reqCap(orZero(r.getRetryAfterFallback()), "retryAfterFallback");
    if (base.compareTo(cap) > 0) {
      throw new IllegalArgumentException("baseBackoff must be <= maxBackoff");
    }

    final Duration tokenWait = reqCap(
        Objects.requireNonNull(r.getTokenWaitTimeout(), "tokenWaitTimeout"),
        "tokenWaitTimeout"
    );
    final Duration bhWait = reqCap(
        Objects.requireNonNull(r.getBulkheadWait(), "bulkheadWait"),
        "bulkheadWait"
    );

    var rlCfg = RateLimiterConfig.custom()
        .limitRefreshPeriod(Objects.requireNonNull(r.getLimitRefreshPeriod(), "limitRefreshPeriod"))
        .limitForPeriod(reqPos(r.getLimitForPeriod(), "limitForPeriod"))
        .timeoutDuration(tokenWait)
        .build();

    var bhCfg = BulkheadConfig.custom()
        .maxConcurrentCalls(reqPos(r.getMaxConcurrentCalls(), "maxConcurrentCalls"))
        .maxWaitDuration(bhWait)
        .build();

    String rlName = name(type, endpointKey, "rl", accountId);
    String bhName = name(type, endpointKey, "bh", accountId);
    String rtName = name(type, endpointKey, "rt", accountId); // Retry per-account

    RateLimiter rl = rlCache.get(rlName, n -> RateLimiter.of(n, rlCfg));
    Bulkhead bh = bhCache.get(bhName, n -> Bulkhead.of(n, bhCfg));
    Retry rt = retryCache.get(rtName,
        n -> buildRetry(maxAttempts, base, cap, jitter, raFallback));

    return new ResilienceKit(rl, bh, rt);
  }

  public <T> Flux<T> apply(Flux<T> src, ResilienceKit k) {
    return applyBulkhead(applyRateLimiter(src, k.rl()), k.bh());
  }

  public <T> Mono<T> apply(Mono<T> src, ResilienceKit k) {
    return applyBulkhead(applyRateLimiter(src, k.rl()), k.bh());
  }

  /**
   * Важно: {@code maxAttempts} включает первую попытку. Т.е. при {@code maxAttempts=3} допускаются
   * 2 повтора (totalRetries() ∈ {0,1,2}; стоп на >=2).
   */
  private Retry buildRetry(int maxAttempts, Duration base, Duration cap, Duration jitter,
      Duration raFallback) {
    return Retry.from(signals -> signals.flatMap(rs -> {
      final Throwable t = rs.failure();

      if (rs.totalRetries() >= maxAttempts - 1) {
        return Mono.error(t);
      }

      if (t instanceof WebClientResponseException w) {
        final int code = w.getStatusCode().value();
        if (code == 429 || code == 503) {
          Duration d = RetryAfterSupport.parse(w.getHeaders(), raFallback);
          return Mono.delay(BackoffSupport.addJitterAndCap(d, jitter, cap));
        }
        if (code == 409 || code == 408 || code == 425 || (code >= 500 && code <= 599)) {
          return Mono.delay(BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter));
        }
        return Mono.error(t);
      }

      if (t instanceof WebClientRequestException req && isTransientNetwork(req)) {
        return Mono.delay(BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter));
      }

      if (t instanceof RequestNotPermitted || t instanceof BulkheadFullException) {
        return Mono.delay(BackoffSupport.expBackoff(rs.totalRetries(), base, cap, jitter));
      }

      return Mono.error(t);
    }));
  }

  private MarketplaceProperties.Resilience effectiveResilience(MarketplaceType type,
      String endpointKey) {
    var p = props.get(type);
    Map<String, MarketplaceProperties.Resilience> o = p.getResilienceOverrides();
    if (o != null) {
      var local = o.get(endpointKey);
      if (local != null) {
        return local;
      }
    }
    return p.getResilience();
  }

  private static String name(MarketplaceType type, String endpoint, String kind, long accountId) {
    return type + "." + endpoint + "." + kind + "." + accountId;
  }

  private static int reqPos(Integer v, String name) {
    Objects.requireNonNull(v, name);
    if (v <= 0) {
      throw new IllegalArgumentException(name + " must be > 0");
    }
    return v;
  }

  private static Duration orZero(Duration d) {
    return d == null ? Duration.ZERO : d;
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
          || c instanceof ClosedChannelException) {
        return true;
      }
    }
    return false;
  }

  /* ---- локальные операторы RL/BH ---- */

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
}
