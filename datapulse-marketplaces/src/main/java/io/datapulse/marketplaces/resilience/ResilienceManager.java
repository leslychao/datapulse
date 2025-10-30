package io.datapulse.marketplaces.resilience;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.endpoint.EndpointKey;
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
import java.util.concurrent.ThreadLocalRandom;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResilienceManager {

  private static final Duration MAX_BACKOFF_CAP = Duration.ofMinutes(5);

  private final MarketplaceProperties marketplaceProperties;

  private final Cache<String, RateLimiter> rateLimiterCache =
      Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();
  private final Cache<String, Bulkhead> bulkheadCache =
      Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();
  private final Cache<String, Retry> retryCache =
      Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

  public <T> Flux<T> apply(Flux<T> source, MarketplaceType marketplaceType, EndpointKey endpointKey,
      long accountId) {
    return doApply(source, marketplaceType, endpointKey, "acc:" + accountId);
  }

  private <T> Flux<T> doApply(Flux<T> source, MarketplaceType marketplaceType,
      EndpointKey endpointKey, String scopeId) {
    ResilienceKit endpointKit = buildKit(marketplaceType, endpointKey, scopeId);
    Optional<ResilienceKit> globalKitOpt = tryBuildGlobalKit(marketplaceType, scopeId);
    Flux<T> guardedStream = globalKitOpt
        .map(globalKit -> applyGuards(applyGuards(source, globalKit), endpointKit))
        .orElseGet(() -> applyGuards(source, endpointKit));
    return guardedStream.retryWhen(endpointKit.retry());
  }

  private Optional<ResilienceKit> tryBuildGlobalKit(MarketplaceType marketplaceType,
      String scopeId) {
    var provider = marketplaceProperties.get(marketplaceType);
    var baseResilience = provider.getResilience();
    if (baseResilience == null) {
      return Optional.empty();
    }
    return Optional.of(buildKit(marketplaceType, null, scopeId));
  }

  private ResilienceKit buildKit(MarketplaceType marketplaceType, EndpointKey endpointKey,
      String scopeId) {
    var provider = marketplaceProperties.get(marketplaceType);
    var cfg = (endpointKey == null)
        ? provider.getResilience().requireAll()
        : provider.effectiveResilience(endpointKey);

    int maxAttempts = requirePositive(cfg.getMaxAttempts(), "maxAttempts");
    Duration baseBackoff = requirePositive(cfg.getBaseBackoff(), "baseBackoff");
    Duration maxBackoff = requireCap(cfg.getMaxBackoff(), "maxBackoff");
    Duration maxJitter = requireNonNegative(orZero(cfg.getMaxJitter()), "maxJitter");
    Duration retryAfterFallback = requireCap(orZero(cfg.getRetryAfterFallback()),
        "retryAfterFallback");
    if (baseBackoff.compareTo(maxBackoff) > 0) {
      throw new IllegalArgumentException("baseBackoff must be <= maxBackoff");
    }
    Duration tokenWaitTimeout = requireCap(
        Objects.requireNonNull(cfg.getTokenWaitTimeout(), "tokenWaitTimeout"),
        "tokenWaitTimeout");
    Duration bulkheadWait = requireCap(
        Objects.requireNonNull(cfg.getBulkheadWait(), "bulkheadWait"),
        "bulkheadWait");

    RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
        .limitRefreshPeriod(
            Objects.requireNonNull(cfg.getLimitRefreshPeriod(), "limitRefreshPeriod"))
        .limitForPeriod(requirePositive(cfg.getLimitForPeriod(), "limitForPeriod"))
        .timeoutDuration(tokenWaitTimeout)
        .build();

    BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
        .maxConcurrentCalls(requirePositive(cfg.getMaxConcurrentCalls(), "maxConcurrentCalls"))
        .maxWaitDuration(bulkheadWait)
        .build();

    String endpointLabel = (endpointKey == null) ? "GLOBAL" : endpointKey.name();
    String rlName = marketplaceType + "." + endpointLabel + ".rl." + scopeId;
    String bhName = marketplaceType + "." + endpointLabel + ".bh." + scopeId;
    String rtName = marketplaceType + "." + endpointLabel + ".rt." + scopeId;
    String contextLabel = marketplaceType + ":" + endpointLabel + ":" + scopeId;

    RateLimiter rateLimiter = rateLimiterCache.get(rlName,
        name -> RateLimiter.of(name, rateLimiterConfig));
    Bulkhead bulkhead = bulkheadCache.get(bhName, name -> Bulkhead.of(name, bulkheadConfig));
    Retry retry = retryCache.get(rtName,
        name -> buildRetry(maxAttempts, baseBackoff, maxBackoff, maxJitter, retryAfterFallback,
            contextLabel));

    return new ResilienceKit(rateLimiter, bulkhead, retry);
  }

  public record ResilienceKit(RateLimiter rateLimiter, Bulkhead bulkhead, Retry retry) {

  }

  private static <T> Flux<T> applyGuards(Flux<T> source, ResilienceKit kit) {
    return applyRateLimiter(applyBulkhead(source, kit.bulkhead()), kit.rateLimiter());
  }

  private static <T> Mono<T> applyGuards(Mono<T> source, ResilienceKit kit) {
    return applyRateLimiter(applyBulkhead(source, kit.bulkhead()), kit.rateLimiter());
  }

  private static <T> Flux<T> applyBulkhead(Flux<T> source, Bulkhead bulkhead) {
    return Flux.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Flux.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      return source.doFinally(signal -> bulkhead.onComplete());
    });
  }

  private static <T> Mono<T> applyBulkhead(Mono<T> source, Bulkhead bulkhead) {
    return Mono.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Mono.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      return source.doFinally(signal -> bulkhead.onComplete());
    });
  }

  private static <T> Flux<T> applyRateLimiter(Flux<T> source, RateLimiter rateLimiter) {
    return Flux.defer(() -> {
      long waitNanos = rateLimiter.reservePermission();
      if (waitNanos < 0) {
        return Flux.error(RequestNotPermitted.createRequestNotPermitted(rateLimiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? source : source.delaySubscription(wait);
    });
  }

  private static <T> Mono<T> applyRateLimiter(Mono<T> source, RateLimiter rateLimiter) {
    return Mono.defer(() -> {
      long waitNanos = rateLimiter.reservePermission();
      if (waitNanos < 0) {
        return Mono.error(RequestNotPermitted.createRequestNotPermitted(rateLimiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? source : source.delaySubscription(wait);
    });
  }

  private Retry buildRetry(int maxAttempts, Duration baseBackoff, Duration maxBackoff,
      Duration jitter, Duration retryAfterFallback, String contextLabel) {
    return Retry.from(signals -> signals.flatMap(retrySignal -> {
      Throwable failure = retrySignal.failure();
      long attempt = retrySignal.totalRetries() + 1;

      if (retrySignal.totalRetries() >= maxAttempts - 1) {
        log.warn("[{}] retry exhausted after {} attempts, last-cause={}", contextLabel, maxAttempts,
            simpleClassName(failure));
        return Mono.error(failure);
      }

      if (failure instanceof WebClientResponseException responseEx) {
        int code = responseEx.getStatusCode().value();
        if (code == 429 || code == 503) {
          Duration retryAfter = RetryAfterSupport.parse(responseEx.getHeaders(),
              retryAfterFallback);
          Duration delay = BackoffMath.addJitterAndCap(retryAfter, jitter, maxBackoff);
          log.info("[{}] {} -> retry (#{} ) in {} (headers={}, jitter={}, cap={})",
              contextLabel, code, attempt, delay, shortRetryHeaders(responseEx), jitter,
              maxBackoff);
          return Mono.delay(delay);
        }
        if (code == 409 || code == 408 || code == 425 || (code >= 500 && code <= 599)) {
          Duration delay = BackoffMath.expBackoff(retrySignal.totalRetries(), baseBackoff,
              maxBackoff, jitter);
          log.info("[{}] {} -> retry (#{} ) in {} (base={}, jitter={}, cap={})",
              contextLabel, code, attempt, delay, baseBackoff, jitter, maxBackoff);
          return Mono.delay(delay);
        }
        log.warn("[{}] non-retryable status={}, giving up (#{} )", contextLabel, code, attempt);
        return Mono.error(failure);
      }

      if (failure instanceof WebClientRequestException requestEx && isTransientNetwork(requestEx)) {
        Duration delay = BackoffMath.expBackoff(retrySignal.totalRetries(), baseBackoff, maxBackoff,
            jitter);
        log.info("[{}] transient network -> retry (#{} ) in {} (base={}, jitter={}, cap={})",
            contextLabel, attempt, delay, baseBackoff, jitter, maxBackoff);
        return Mono.delay(delay);
      }

      if (failure instanceof RequestNotPermitted || failure instanceof BulkheadFullException) {
        Duration delay = BackoffMath.expBackoff(retrySignal.totalRetries(), baseBackoff, maxBackoff,
            jitter);
        log.info("[{}] RL/BH blocked -> retry (#{} ) in {} (base={}, jitter={}, cap={})",
            contextLabel, attempt, delay, baseBackoff, jitter, maxBackoff);
        return Mono.delay(delay);
      }

      log.warn("[{}] non-retryable exception={}, giving up (#{} )", contextLabel,
          simpleClassName(failure), attempt);
      return Mono.error(failure);
    }));
  }

  private MarketplaceProperties.Resilience resolveResilience(MarketplaceType marketplaceType,
      EndpointKey endpointKey) {
    var provider = marketplaceProperties.get(marketplaceType);
    return (endpointKey == null)
        ? provider.getResilience().requireAll()
        : provider.effectiveResilience(endpointKey);
  }

  private static Duration orZero(Duration value) {
    return value == null ? Duration.ZERO : value;
  }

  private static int requirePositive(Integer value, String name) {
    Objects.requireNonNull(value, name);
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be > 0");
    }
    return value;
  }

  private static Duration requireCap(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isNegative() || value.compareTo(MAX_BACKOFF_CAP) > 0) {
      throw new IllegalArgumentException(
          name + " must be in [0.." + MAX_BACKOFF_CAP.toMinutes() + "m]");
    }
    return value;
  }

  private static Duration requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be > 0");
    }
    return value;
  }

  private static Duration requireNonNegative(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isNegative()) {
      throw new IllegalArgumentException(name + " must be >= 0");
    }
    return value;
  }

  private static boolean isTransientNetwork(Throwable throwable) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof SocketTimeoutException
          || cause instanceof ConnectException
          || cause instanceof NoRouteToHostException
          || cause instanceof UnknownHostException
          || cause instanceof SSLException
          || cause instanceof ClosedChannelException
          || cause instanceof reactor.netty.http.client.PrematureCloseException) {
        return true;
      }
    }
    return false;
  }

  private static String simpleClassName(Throwable throwable) {
    return (throwable == null) ? "null" : throwable.getClass().getSimpleName();
  }

  private static String shortRetryHeaders(WebClientResponseException ex) {
    var headers = ex.getHeaders();
    String retryAfter = headers.getFirst("Retry-After");
    String xRetry = headers.getFirst("X-Ratelimit-Retry");
    String xReset = headers.getFirst("X-Ratelimit-Reset");
    return "retry-after=" + (retryAfter == null ? "-" : retryAfter)
        + ", x-ratelimit-retry=" + (xRetry == null ? "-" : xRetry)
        + ", x-ratelimit-reset=" + (xReset == null ? "-" : xReset);
  }

  private static final class BackoffMath {

    private BackoffMath() {
    }

    static Duration expBackoff(long retries, Duration base, Duration cap, Duration jitter) {
      long attempt = Math.max(1, retries + 1);
      long factor = attempt >= 63 ? Long.MAX_VALUE : (1L << (attempt - 1));
      Duration expDelay;
      try {
        expDelay = base.multipliedBy(factor);
      } catch (ArithmeticException e) {
        expDelay = Duration.ofMillis(Long.MAX_VALUE);
      }
      if (expDelay.compareTo(cap) > 0) {
        expDelay = cap;
      }
      if (jitter == null || jitter.isZero() || jitter.isNegative()) {
        return expDelay;
      }
      long bound = Math.max(0, jitter.toMillis());
      long rnd = (bound == 0) ? 0 : ThreadLocalRandom.current().nextLong(bound + 1);
      try {
        Duration withJitter = expDelay.plusMillis(rnd);
        return withJitter.compareTo(cap) > 0 ? cap : withJitter;
      } catch (ArithmeticException e) {
        return cap;
      }
    }

    static Duration addJitterAndCap(Duration baseDelay, Duration jitter, Duration cap) {
      if (jitter == null || jitter.isZero() || jitter.isNegative()) {
        return baseDelay.compareTo(cap) > 0 ? cap : baseDelay;
      }
      long bound = Math.max(0, jitter.toMillis());
      long rnd = (bound == 0) ? 0 : ThreadLocalRandom.current().nextLong(bound + 1);
      Duration withJitter;
      try {
        withJitter = baseDelay.plusMillis(rnd);
      } catch (ArithmeticException e) {
        withJitter = cap;
      }
      return withJitter.compareTo(cap) > 0 ? cap : withJitter;
    }
  }

  public static final class RetryAfterSupport {

    private static final String XR_RETRY = "X-Ratelimit-Retry";
    private static final String XR_RESET = "X-Ratelimit-Reset";
    private static final String RL_RESET = "RateLimit-Reset";
    private static final String RL_RESET_AFTER = "RateLimit-Reset-After";

    private RetryAfterSupport() {
    }

    public static Duration parse(HttpHeaders headers, Duration fallback) {
      return parse(headers, Clock.systemUTC(), fallback);
    }

    static Duration parse(HttpHeaders headers, Clock clock, Duration fallback) {
      Optional<Duration> candidate = java.util.stream.Stream.<Optional<Duration>>of(
              header(headers, XR_RETRY).flatMap(RetryAfterSupport::parseDeltaSeconds),
              header(headers, XR_RESET)
                  .or(() -> header(headers, RL_RESET))
                  .or(() -> header(headers, RL_RESET_AFTER))
                  .flatMap(value -> parseReset(value, clock)),
              header(headers, HttpHeaders.RETRY_AFTER).flatMap(value ->
                  parseDeltaSeconds(value).or(() -> parseHttpDate(value, clock)))
          )
          .flatMap(Optional::stream)
          .findFirst();
      return candidate.map(RetryAfterSupport::nonNegative).orElse(fallback);
    }

    private static Optional<String> header(HttpHeaders headers, String name) {
      String value = headers.getFirst(name);
      if (value == null && name.startsWith("X-Ratelimit-")) {
        value = headers.getFirst(name.replace("Ratelimit", "RateLimit"));
      }
      return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    private static Optional<Duration> parseDeltaSeconds(String value) {
      try {
        String s = value.charAt(0) == '+' ? value.substring(1) : value;
        BigDecimal number = new BigDecimal(s);
        if (number.signum() < 0) {
          return Optional.empty();
        }
        long ceilSec = number.setScale(0, RoundingMode.CEILING).longValueExact();
        return Optional.of(Duration.ofSeconds(ceilSec));
      } catch (RuntimeException ignore) {
        return Optional.empty();
      }
    }

    private static Optional<Duration> parseReset(String value, Clock clock) {
      var delta = parseDeltaSeconds(value);
      if (delta.isPresent()) {
        return delta;
      }
      try {
        String s = value.trim();
        long n = Long.parseLong(s);
        Instant when = (s.length() >= 12) ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
        return Optional.of(Duration.between(Instant.now(clock), when));
      } catch (RuntimeException ignore) {
        return Optional.empty();
      }
    }

    private static Optional<Duration> parseHttpDate(String value, Clock clock) {
      try {
        ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
        return Optional.of(Duration.between(Instant.now(clock), when.toInstant()));
      } catch (RuntimeException e) {
        return Optional.empty();
      }
    }

    private static Duration nonNegative(Duration value) {
      return value.isNegative() ? Duration.ZERO : value;
    }
  }
}
