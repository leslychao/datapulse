package io.datapulse.core.client;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class ReactorResilienceSupport {

  public <T> Flux<T> applyRateLimiter(Flux<T> source, RateLimiter limiter) {
    return Flux.defer(() -> {
      long waitNanos = limiter.reservePermission();
      if (waitNanos < 0) {
        return Flux.error(RequestNotPermitted.createRequestNotPermitted(limiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? source : source.delaySubscription(wait);
    });
  }

  public <T> Mono<T> applyRateLimiter(Mono<T> source, RateLimiter limiter) {
    return Mono.defer(() -> {
      long waitNanos = limiter.reservePermission();
      if (waitNanos < 0) {
        return Mono.error(RequestNotPermitted.createRequestNotPermitted(limiter));
      }
      Duration wait = Duration.ofNanos(waitNanos);
      return wait.isZero() ? source : source.delaySubscription(wait);
    });
  }

  public static <T> Flux<T> applyBulkhead(Flux<T> source, Bulkhead bulkhead) {
    return Flux.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Flux.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      return source.doFinally(sig -> bulkhead.onComplete());
    });
  }

  public static <T> Mono<T> applyBulkhead(Mono<T> source, Bulkhead bulkhead) {
    return Mono.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Mono.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      return source.doFinally(sig -> bulkhead.onComplete());
    });
  }

  public <T> Flux<T> applyResilience(Flux<T> source, RateLimiter rl, Bulkhead bh) {
    return applyBulkhead(applyRateLimiter(source, rl), bh);
  }

  public <T> Mono<T> applyResilience(Mono<T> source, RateLimiter rl, Bulkhead bh) {
    return applyBulkhead(applyRateLimiter(source, rl), bh);
  }
}
