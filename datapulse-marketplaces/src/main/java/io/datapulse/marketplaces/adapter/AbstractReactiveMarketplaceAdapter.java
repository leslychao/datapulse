package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.MarketplaceExceptions;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointRef;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.MarketplaceRetryService;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
abstract class AbstractReactiveMarketplaceAdapter {

  protected static final int DEFAULT_MAX_PAGES = 10_000;

  protected final StreamingDownloadService streamingDownloadService;
  protected final MarketplaceRetryService retryService;   // ⬅ заменили ResilienceManager
  protected final JsonFluxReader fluxReader;
  protected final HttpHeaderProvider headerProvider;
  protected final CredentialsProvider credentialsProvider;

  protected AbstractReactiveMarketplaceAdapter(
      StreamingDownloadService streamingDownloadService,
      MarketplaceRetryService retryService,
      JsonFluxReader fluxReader,
      HttpHeaderProvider headerProvider,
      CredentialsProvider credentialsProvider
  ) {
    this.streamingDownloadService = Objects.requireNonNull(streamingDownloadService);
    this.retryService = Objects.requireNonNull(retryService);
    this.fluxReader = Objects.requireNonNull(fluxReader);
    this.headerProvider = Objects.requireNonNull(headerProvider);
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
  }

  protected final <T> Flux<T> get(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Class<T> targetType
  ) {
    HttpHeaders headers = buildHeaders(type, accountId);
    return exchange(type, key, accountId, uri, targetType,
        () -> streamingDownloadService.stream(uri, headers))
        .name("mp.get").tag("mp", type.name()).tag("key", key.tag());
  }

  protected final <T> Flux<T> post(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Map<String, ?> body,
      Class<T> targetType
  ) {
    HttpHeaders headers = buildHeaders(type, accountId);
    return exchange(type, key, accountId, uri, targetType,
        () -> streamingDownloadService.post(uri, headers, body))
        .name("mp.post").tag("mp", type.name()).tag("key", key.tag());
  }

  private <T> Flux<T> exchange(
      MarketplaceType type, EndpointKey key, long accountId, URI uri,
      Class<T> targetType, Supplier<Flux<DataBuffer>> requestSupplier
  ) {
    if (uri == null) throw new AppException(MessageCodes.URI_REQUIRED);
    return execute(type, key, accountId, uri, targetType, requestSupplier.get());
  }

  protected final <T> Flux<T> mergeGet(
      MarketplaceType type, long accountId, List<EndpointRef> refs, Class<T> elementType
  ) {
    return merge(refs, r -> get(type, r.key(), accountId, r.uri(), elementType));
  }

  protected final <T> Flux<T> mergePost(
      MarketplaceType type, long accountId, List<EndpointRef> refs, Map<String, ?> body,
      Class<T> elementType
  ) {
    return merge(refs, r -> post(type, r.key(), accountId, r.uri(), body, elementType));
  }

  private <T> Flux<T> merge(List<EndpointRef> refs, Function<EndpointRef, Flux<T>> invoker) {
    if (refs == null || refs.isEmpty()) return Flux.empty();
    if (refs.size() == 1) return invoker.apply(refs.get(0));
    return Flux.fromIterable(refs).flatMapDelayError(invoker, Integer.MAX_VALUE, 32);
  }

  public record Page<T>(List<T> items, String nextCursor) {}

  protected final <T> Flux<T> paginate(
      Supplier<Mono<Page<T>>> firstPage,
      Function<String, Mono<Page<T>>> nextPageByCursor
  ) {
    return paginate(firstPage, nextPageByCursor, DEFAULT_MAX_PAGES);
  }

  protected final <T> Flux<T> paginate(
      Supplier<Mono<Page<T>>> firstPage,
      Function<String, Mono<Page<T>>> nextPageByCursor,
      int maxPages
  ) {
    final Set<String> seen = Collections.newSetFromMap(new ConcurrentHashMap<>());
    return Mono.defer(firstPage)
        .expand(p -> {
          String next = p.nextCursor();
          if (next == null || next.isBlank()) return Mono.empty();
          if (!seen.add(next)) return Mono.empty();
          if (seen.size() >= maxPages) return Mono.empty();
          return nextPageByCursor.apply(next);
        })
        .flatMapIterable(Page::items);
  }

  private <T> Flux<T> execute(
      MarketplaceType marketplace,
      EndpointKey key,
      long accountId,
      URI uri,
      Class<T> targetType,
      Flux<DataBuffer> raw
  ) {
    if (targetType == null) throw new AppException(MessageCodes.TYPE_REQUIRED);

    final String ctx = marketplace + ":" + key.tag() + ":acc=" + accountId;

    // ⬇ только Retry, больше ничего
    Flux<DataBuffer> guarded = retryService.withRetries(raw, marketplace, key)
        .name("mp.http")
        .tag("mp", marketplace.name())
        .tag("key", key.tag())
        .doOnSubscribe(s -> log.debug("[{}] start {}", ctx, safeUri(uri)))
        .doFinally(st -> log.debug("[{}] done st={} {}", ctx, st, safeUri(uri)))
        .onErrorMap(ex -> {
          if (ex instanceof CancellationException || Exceptions.isCancel(ex)) return ex;
          return new MarketplaceExceptions.FetchFailed(
              ex, MessageCodes.MARKETPLACE_FETCH_FAILED, marketplace, key.tag(), accountId, uri);
        })
        .checkpoint("mp:" + marketplace + "." + key.tag(), true);

    return fluxReader.readArray(guarded, targetType)
        .onErrorMap(ex -> {
          if (ex instanceof CancellationException || Exceptions.isCancel(ex)) return ex;
          return new MarketplaceExceptions.ParseFailed(
              ex, MessageCodes.MARKETPLACE_PARSE_FAILED, marketplace, key.tag(), accountId, uri);
        });
  }

  private static String safeUri(URI uri) {
    return (uri == null) ? "null" : uri.getPath();
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }
}
