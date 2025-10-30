package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.MarketplaceExceptions;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointRef;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.ResilienceManager;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Базовый реактивный адаптер: - RL/BH/Retry до декодирования - 1→N merge с конкурентностью из YAML
 * - без Object в API (POST принимает Map<String, ?>) - наблюдаемость (name/tag/checkpoint)
 */
@Slf4j
abstract class AbstractReactiveMarketplaceAdapter {

  protected static final int DEFAULT_PREFETCH = 32;

  protected final StreamingDownloadService streamingDownloadService;
  protected final ResilienceManager resilienceManager;
  protected final MarketplaceProperties properties;
  protected final JsonFluxReader fluxReader;
  protected final HttpHeaderProvider headerProvider;
  protected final CredentialsProvider credentialsProvider;

  protected AbstractReactiveMarketplaceAdapter(
      StreamingDownloadService streamingDownloadService,
      ResilienceManager resilienceManager,
      MarketplaceProperties properties,
      JsonFluxReader fluxReader,
      HttpHeaderProvider headerProvider,
      CredentialsProvider credentialsProvider
  ) {
    this.streamingDownloadService = Objects.requireNonNull(streamingDownloadService);
    this.resilienceManager = Objects.requireNonNull(resilienceManager);
    this.properties = Objects.requireNonNull(properties);
    this.fluxReader = Objects.requireNonNull(fluxReader);
    this.headerProvider = Objects.requireNonNull(headerProvider);
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
  }

  // ---------- HTTP primitives (без Object) ----------

  protected final <T> Flux<T> get(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Class<T> targetType
  ) {
    HttpHeaders headers = buildHeaders(type, accountId);
    Flux<DataBuffer> raw = streamingDownloadService.stream(uri, headers);
    return execute(type, key, accountId, uri, targetType, raw)
        .name("mp.get").tag("mp", type.name()).tag("key", key.tag());
  }

  protected final <T> Flux<T> post(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Map<String, ?> body,
      Class<T> targetType
  ) {
    HttpHeaders headers = buildHeaders(type, accountId);
    Flux<DataBuffer> raw = streamingDownloadService.post(uri, headers, body);
    return execute(type, key, accountId, uri, targetType, raw)
        .name("mp.post").tag("mp", type.name()).tag("key", key.tag());
  }

  // ---------- Fan-out helpers (1→N) ----------

  protected final <T> Flux<T> mergeGet(
      MarketplaceType type, long accountId, List<EndpointRef> refs, Class<T> elementType
  ) {
    if (refs == null || refs.isEmpty()) {
      return Flux.empty();
    }
    if (refs.size() == 1) {
      var r = refs.get(0);
      return this.get(type, r.key(), accountId, r.uri(), elementType);
    }
    int concurrency = concurrencyFor(type, refs);
    return Flux.fromIterable(refs)
        .flatMapDelayError(
            ref -> this.get(type, ref.key(), accountId, ref.uri(), elementType),
            concurrency,
            DEFAULT_PREFETCH
        );
  }

  protected final <T> Flux<T> mergePost(
      MarketplaceType type, long accountId, List<EndpointRef> refs, Map<String, ?> body,
      Class<T> elementType
  ) {
    if (refs == null || refs.isEmpty()) {
      return Flux.empty();
    }
    if (refs.size() == 1) {
      var r = refs.get(0);
      return this.post(type, r.key(), accountId, r.uri(), body, elementType);
    }
    int concurrency = concurrencyFor(type, refs);
    return Flux.fromIterable(refs)
        .flatMapDelayError(
            ref -> this.post(type, ref.key(), accountId, ref.uri(), body, elementType),
            concurrency,
            DEFAULT_PREFETCH
        );
  }

  /**
   * min(refs.size(), min(effectiveMaxConcurrentCalls(key)), global).
   */
  protected final int concurrencyFor(MarketplaceType type, List<EndpointRef> refs) {
    var provider = properties.get(type);
    int globalCap = provider.getResilience().requireAll().getMaxConcurrentCalls();
    int minEndpointCap = refs.stream()
        .map(EndpointRef::key)
        .mapToInt(provider::effectiveMaxConcurrentCalls)
        .min().orElse(globalCap);
    int sources = refs.size();
    return Math.max(1, Math.min(sources, Math.min(minEndpointCap, globalCap)));
  }

  // ---------- Универсальная пагинация (optional) ----------

  public record Page<T>(List<T> items, String nextCursor) {

  }

  protected final <T> Flux<T> paginate(
      Supplier<Mono<Page<T>>> firstPage,
      Function<String, Mono<Page<T>>> nextPageByCursor
  ) {
    return Mono.defer(firstPage)
        .expand(p -> {
          var next = p.nextCursor();
          return (next == null || next.isBlank()) ? Mono.empty() : nextPageByCursor.apply(next);
        })
        .flatMapIterable(Page::items);
  }

  // ---------- internal ----------

  private <T> Flux<T> execute(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Class<T> targetType,
      Flux<DataBuffer> raw
  ) {
    if (uri == null) {
      throw new AppException(MessageCodes.URI_REQUIRED);
    }
    if (targetType == null) {
      throw new AppException(MessageCodes.TYPE_REQUIRED);
    }

    final String ctx = type + ":" + key.tag() + ":acc=" + accountId;

    Flux<DataBuffer> guarded = resilienceManager.apply(raw, type, key, accountId)
        .name("mp.http").tag("mp", type.name()).tag("key", key.tag())
        .doOnSubscribe(s -> log.debug("[{}] start {}", ctx, uri))
        .doFinally(st -> log.debug("[{}] done st={} {}", ctx, st, uri))
        .onErrorMap(ex -> {
          if (ex instanceof CancellationException || Exceptions.isCancel(ex)) {
            return ex;
          }
          return new MarketplaceExceptions.FetchFailed(ex, MessageCodes.MARKETPLACE_FETCH_FAILED,
              type, key.tag(), accountId, uri);
        })
        .checkpoint("mp:" + type + "." + key.tag(), true);

    return fluxReader.readArray(guarded, targetType)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, type, key.tag(), accountId, uri));
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }
}
