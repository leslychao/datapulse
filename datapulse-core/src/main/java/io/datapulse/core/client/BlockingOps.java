package io.datapulse.core.client;

import java.util.concurrent.Callable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class BlockingOps {

  private BlockingOps() { }

  public static <T> Mono<T> supplyBlocking(Callable<T> supplier) {
    return Mono.create(sink ->
        Schedulers.boundedElastic().schedule(() -> {
          try {
            sink.success(supplier.call());
          } catch (Throwable t) {
            sink.error(t);
          }
        })
    );
  }

  public static Mono<Void> runBlocking(Runnable runnable) {
    return Mono.create(sink ->
        Schedulers.boundedElastic().schedule(() -> {
          try {
            runnable.run();
            sink.success();
          } catch (Throwable t) {
            sink.error(t);
          }
        })
    );
  }
}
