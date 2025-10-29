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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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

  private final MarketplaceProperties marketplaceProperties;

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

  /* ====== Core impl ====== */

  private <T> Flux<T> doApply(Flux<T> raw, MarketplaceType type, String endpointKey, String limiterKey) {
    var epKit = doBuildKit(type, endpointKey, limiterKey);
    var globalOpt = tryBuildGlobalKit(type, limiterKey);

    Flux<T> withGuards = globalOpt
        .map(gk -> apply(apply(raw, gk), epKit)) // GLOBAL → PER-ENDPOINT
        .orElseGet(() -> apply(raw, epKit));

    // Ретраи — пер-эндпоинт (контекст логов и политики привязаны к конкретному API)
    return withGuards.retryWhen(epKit.retry());
  }

  /** Пытается построить GLOBAL-kit на основе базовой resilience-конфигурации провайдера. */
  private Optional<ResilienceKit> tryBuildGlobalKit(MarketplaceType type, String limiterKey) {
    MarketplaceProperties.Provider provider = marketplaceProperties.get(type);
    MarketplaceProperties.Resilience base = provider.getResilience();
    if (base == null) {
      return Optional.empty();
    }
    return Optional.of(doBuildKit(type, null, limiterKey));
  }

  /** Строим комплект для (type, endpointKey, limiterKey). */
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
    final Duration tokenWait = reqCap(Objects.requireNonNull(r.getTokenWaitTimeout(), "tokenWaitTimeout"), "tokenWaitTimeout");
    final Duration bhWait = reqCap(Objects.requireNonNull(r.getBulkheadWait(), "bulkheadWait"), "bulkheadWait");

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
    Retry rt = retryCache.get(rtName, n -> buildRetry(maxAttempts, base, cap, jitter, raFallback, ctx));

    return new ResilienceKit(rl, bh, rt);
  }

  /** Комплект ограничителей и Retry. */
  public record ResilienceKit(RateLimiter rl, Bulkhead bh, Retry retry) {}

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
        log.warn("[{}] retry exhausted after {} attempts, last-cause={}", ctx, maxAttempts, simpleName(t));
        return Mono.error(t);
      }

      if (t instanceof WebClientResponseException w) {
        final int code = w.getStatusCode().value();
        if (code == 429 || code == 503) {
          Duration ra = RetryAfterSupport.parse(w.getHeaders(), raFallback);
          Duration delay = BackoffMath.addJitterAndCap(ra, jitter, cap);
          log.info("[{}] {} -> retry (attempt #{}) in {} (headers={}, jitter={}, cap={})",
              ctx, code, attempt, delay, shortRetryAfterHeaders(w), jitter, cap);
          return Mono.delay(delay);
        }
        if (code == 409 || code == 408 || code == 425 || (code >= 500 && code <= 599)) {
          Duration delay = BackoffMath.expBackoff(rs.totalRetries(), base, cap, jitter);
          log.info("[{}] {} -> retry (attempt #{}) in {} (base={}, jitter={}, cap={})",
              ctx, code, attempt, delay, base, jitter, cap);
          return Mono.delay(delay);
        }
        log.warn("[{}] non-retryable status={}, giving up (attempt #{})", ctx, code, attempt);
        return Mono.error(t);
      }

      if (t instanceof WebClientRequestException req && isTransientNetwork(req)) {
        Duration delay = BackoffMath.expBackoff(rs.totalRetries(), base, cap, jitter);
        log.info("[{}] transient network -> retry (attempt #{}) in {} (base={}, jitter={}, cap={})",
            ctx, attempt, delay, base, jitter, cap);
        return Mono.delay(delay);
      }

      if (t instanceof RequestNotPermitted || t instanceof BulkheadFullException) {
        Duration delay = BackoffMath.expBackoff(rs.totalRetries(), base, cap, jitter);
        log.info("[{}] RL/BH blocked -> retry (attempt #{}) in {} (base={}, jitter={}, cap={})",
            ctx, attempt, delay, base, jitter, cap);
        return Mono.delay(delay);
      }

      log.warn("[{}] non-retryable exception={}, giving up (attempt #{})", ctx, simpleName(t), attempt);
      return Mono.error(t);
    }));
  }

  /* ====== Конфиг и хелперы ====== */

  private MarketplaceProperties.Resilience effectiveResilience(MarketplaceType type, String endpointKey) {
    var provider = marketplaceProperties.get(type); // бросит AppException, если провайдера нет
    var overrides = provider.getResilienceOverrides();

    // 1) endpoint override
    if (overrides != null) {
      var local = overrides.get(endpointKey);
      if (local != null) {
        return local;
      }
    }

    // 2) базовая конфигурация (если нет — это ошибка конфигурации)
    var base = provider.getResilience();
    if (base == null) {
      throw new IllegalStateException(
          "Missing resilience configuration for provider " + type
              + " (neither base nor override defined for endpoint=" + endpointKey + ")"
      );
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

  /* ====== Вложенная математика backoff/jitter/cap ====== */
  private static final class BackoffMath {
    private BackoffMath() {}

    /** Экспоненциальный backoff с «ceil»-капом и add-джиттером. */
    static Duration expBackoff(long retries, Duration base, Duration cap, Duration jitter) {
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
      if (jitter == null || jitter.isZero() || jitter.isNegative()) {
        return exp;
      }

      long bound = Math.max(0, jitter.toMillis());
      long rand = (bound == 0) ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong(bound + 1);

      try {
        Duration withJitter = exp.plusMillis(rand);
        return withJitter.compareTo(cap) > 0 ? cap : withJitter;
      } catch (ArithmeticException e) {
        return cap;
      }
    }

    /** Добавляет джиттер к базовой задержке и применяет верхний кап. */
    static Duration addJitterAndCap(Duration baseDelay, Duration jitter, Duration cap) {
      if (jitter == null || jitter.isZero() || jitter.isNegative()) {
        return baseDelay.compareTo(cap) > 0 ? cap : baseDelay;
      }
      long bound = Math.max(0, jitter.toMillis());
      long rand = (bound == 0) ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong(bound + 1);
      Duration withJitter;
      try {
        withJitter = baseDelay.plusMillis(rand);
      } catch (ArithmeticException e) {
        withJitter = cap;
      }
      return withJitter.compareTo(cap) > 0 ? cap : withJitter;
    }
  }

  /* ====== Вложенный парсер Retry-After / RateLimit-* ====== */
  public static final class RetryAfterSupport {
    private static final String XR_RETRY = "X-Ratelimit-Retry";
    private static final String XR_RESET = "X-Ratelimit-Reset";
    private static final String RL_RESET = "RateLimit-Reset";
    private static final String RL_RESET_AFTER = "RateLimit-Reset-After";

    private RetryAfterSupport() {}

    /** Приоритет: X-RateLimit-Retry → X-RateLimit-Reset / RateLimit-Reset / RateLimit-Reset-After → Retry-After. */
    public static Duration parse(HttpHeaders headers, Duration fallback) {
      return parse(headers, Clock.systemUTC(), fallback);
    }

    static Duration parse(HttpHeaders h, Clock clock, Duration fallback) {
      Optional<Duration> candidate = Stream.<Optional<Duration>>of(
              header(h, XR_RETRY).flatMap(RetryAfterSupport::parseDeltaSeconds),

              // reset: сначала как дельта, иначе epoch s/ms
              header(h, XR_RESET)
                  .or(() -> header(h, RL_RESET))
                  .or(() -> header(h, RL_RESET_AFTER))
                  .flatMap(v -> parseReset(v, clock)),

              // Retry-After: либо дельта, либо RFC1123
              header(h, HttpHeaders.RETRY_AFTER).flatMap(v ->
                  parseDeltaSeconds(v).or(() -> parseHttpDate(v, clock)))
          )
          .flatMap(Optional::stream)
          .findFirst();

      return candidate.map(RetryAfterSupport::nonNegative).orElse(fallback);
    }

    /* -------- helpers -------- */

    private static Optional<String> header(HttpHeaders h, String name) {
      String v = h.getFirst(name);
      if (v == null && name.startsWith("X-Ratelimit-")) {
        v = h.getFirst(name.replace("Ratelimit", "RateLimit")); // доп. регистр/вариант
      }
      return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v.trim());
    }

    /** "+10", "0.4" → ceil секунд; отрицательные → empty. */
    private static Optional<Duration> parseDeltaSeconds(String v) {
      try {
        String s = v.charAt(0) == '+' ? v.substring(1) : v;
        BigDecimal bd = new BigDecimal(s);
        if (bd.signum() < 0) {
          return Optional.empty();
        }
        long ceilSec = bd.setScale(0, RoundingMode.CEILING).longValueExact();
        return Optional.of(Duration.ofSeconds(ceilSec));
      } catch (RuntimeException ignore) {
        return Optional.empty();
      }
    }

    /** Reset: пробуем как дельту, иначе epoch (len>=12 → millis, иначе seconds). */
    private static Optional<Duration> parseReset(String v, Clock clock) {
      var delta = parseDeltaSeconds(v);
      if (delta.isPresent()) {
        return delta;
      }
      try {
        String s = v.trim();
        long n = Long.parseLong(s);
        Instant when = (s.length() >= 12) ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
        return Optional.of(Duration.between(Instant.now(clock), when));
      } catch (RuntimeException ignore) {
        return Optional.empty();
      }
    }

    private static Optional<Duration> parseHttpDate(String v, Clock clock) {
      try {
        ZonedDateTime when = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
        return Optional.of(Duration.between(Instant.now(clock), when.toInstant()));
      } catch (RuntimeException e) {
        return Optional.empty();
      }
    }

    private static Duration nonNegative(Duration d) {
      return d.isNegative() ? Duration.ZERO : d;
    }
  }
}
