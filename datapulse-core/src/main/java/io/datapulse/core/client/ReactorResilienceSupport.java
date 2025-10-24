package io.datapulse.core.client;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class ReactorResilienceSupport {

  public <T> Flux<T> applyRateLimiter(Flux<T> source, RateLimiter limiter) {
    return Flux.defer(() -> {
      limiter.acquirePermission();
      return source;
    });
  }

  public <T> Mono<T> applyRateLimiter(Mono<T> source, RateLimiter limiter) {
    return Mono.defer(() -> {
      limiter.acquirePermission();
      return source;
    });
  }

  public static <T> Flux<T> applyBulkhead(Flux<T> source, Bulkhead bulkhead) {
    return Flux.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Flux.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      AtomicBoolean released = new AtomicBoolean(false);
      return source.doFinally(signal -> {
        if (released.compareAndSet(false, true)) {
          bulkhead.onComplete();
        }
      });
    });
  }

  public static <T> Mono<T> applyBulkhead(Mono<T> source, Bulkhead bulkhead) {
    return Mono.defer(() -> {
      if (!bulkhead.tryAcquirePermission()) {
        return Mono.error(BulkheadFullException.createBulkheadFullException(bulkhead));
      }
      AtomicBoolean released = new AtomicBoolean(false);
      return source.doFinally(signal -> {
        if (released.compareAndSet(false, true)) {
          bulkhead.onComplete();
        }
      });
    });
  }

  public <T> Flux<T> applyResilience(Flux<T> source, RateLimiter rl, Bulkhead bh) {
    return applyBulkhead(applyRateLimiter(source, rl), bh);
  }

  public <T> Mono<T> applyResilience(Mono<T> source, RateLimiter rl, Bulkhead bh) {
    return applyBulkhead(applyRateLimiter(source, rl), bh);
  }
}
