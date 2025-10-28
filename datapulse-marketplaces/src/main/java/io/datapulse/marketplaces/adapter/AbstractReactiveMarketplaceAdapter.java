package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.MarketplaceExceptions;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.ResilienceFactory;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * Базовый адаптер для всех маркетплейсов. Минималистичный и универсальный: - Адаптер сам указывает
 * endpointKey ("sales", "stock", "finance", "reviews"). - База управляет Resilience (rateLimiter /
 * bulkhead / retry). - Единая обработка ошибок и парсинга.
 */
abstract class AbstractReactiveMarketplaceAdapter {

  protected final StreamingDownloadService streamingDownloadService;
  protected final ResilienceFactory resilienceFactory;
  protected final JsonFluxReader fluxReader;
  protected final HttpHeaderProvider headerProvider;
  protected final CredentialsProvider credentialsProvider;

  protected AbstractReactiveMarketplaceAdapter(
      StreamingDownloadService streamingDownloadService,
      ResilienceFactory resilienceFactory,
      JsonFluxReader fluxReader,
      HttpHeaderProvider headerProvider,
      CredentialsProvider credentialsProvider
  ) {
    this.streamingDownloadService = Objects.requireNonNull(streamingDownloadService);
    this.resilienceFactory = Objects.requireNonNull(resilienceFactory);
    this.fluxReader = Objects.requireNonNull(fluxReader);
    this.headerProvider = Objects.requireNonNull(headerProvider);
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
  }

  /**
   * GET с явным указанием endpointKey
   */
  protected final <T> Flux<T> get(MarketplaceType type, String endpointKey, long accountId, URI uri,
      Class<T> targetType) {
    return execute(type, endpointKey, accountId, uri, targetType, () ->
        streamingDownloadService.stream(
            uri,
            buildHeaders(type, accountId),
            retry(type, endpointKey),
            rateLimiter(type, endpointKey, accountId),
            bulkhead(type, endpointKey, accountId)
        ));
  }

  /**
   * POST с явным указанием endpointKey
   */
  protected final <T> Flux<T> post(MarketplaceType type, String endpointKey, long accountId,
      URI uri, Object body, Class<T> targetType) {
    return execute(type, endpointKey, accountId, uri, targetType, () ->
        streamingDownloadService.post(
            uri,
            buildHeaders(type, accountId),
            body,
            retry(type, endpointKey),
            rateLimiter(type, endpointKey, accountId),
            bulkhead(type, endpointKey, accountId)
        ));
  }

  private <T> Flux<T> execute(
      MarketplaceType type, String endpointKey, long accountId, URI uri, Class<T> targetType,
      Supplier<Flux<DataBuffer>> call
  ) {
    if (uri == null) {
      throw new AppException(MessageCodes.URI_REQUIRED);
    }
    if (targetType == null) {
      throw new AppException(MessageCodes.TYPE_REQUIRED);
    }

    Flux<DataBuffer> bytes = call.get()
        .onErrorMap(ex -> new MarketplaceExceptions.FetchFailed(
            ex, MessageCodes.MARKETPLACE_FETCH_FAILED, type, endpointKey, accountId, uri));

    return fluxReader.readArray(bytes, targetType)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, type, endpointKey, accountId, uri));
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }

  private Bulkhead bulkhead(MarketplaceType type, String key, long accountId) {
    return resilienceFactory.bulkhead(type, key, accountId);
  }

  private Retry retry(MarketplaceType type, String key) {
    return resilienceFactory.retry(type, key);
  }

  private RateLimiter rateLimiter(MarketplaceType type, String key, long accountId) {
    return resilienceFactory.rateLimiter(type, key, accountId);
  }
}
