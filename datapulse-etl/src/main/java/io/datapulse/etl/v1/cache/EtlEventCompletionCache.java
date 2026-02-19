package io.datapulse.etl.v1.cache;

import io.datapulse.etl.MarketplaceEvent;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public final class EtlEventCompletionCache {

  private record Key(long accountId, MarketplaceEvent event) {

  }

  private final Map<Key, CompletableFuture<Void>> completions = new ConcurrentHashMap<>();

  public void markCompleted(long accountId, MarketplaceEvent event) {
    Key key = new Key(accountId, event);
    CompletableFuture<Void> future = completions.computeIfAbsent(
        key,
        k -> new CompletableFuture<>()
    );
    if (!future.isDone()) {
      future.complete(null);
    }
  }

  public CompletableFuture<Void> completionFuture(long accountId, MarketplaceEvent event) {
    Key key = new Key(accountId, event);
    return completions.computeIfAbsent(key, k -> new CompletableFuture<>());
  }

  public CompletableFuture<Void> completionFutureForAll(
      long accountId,
      Collection<MarketplaceEvent> events
  ) {
    CompletableFuture<?>[] futures = events.stream()
        .map(event -> completionFuture(accountId, event))
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
  }
}
