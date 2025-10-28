package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.MarketplaceExceptions;
import io.datapulse.marketplaces.endpoints.EndpointKey;
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

  protected final <T> Flux<T> get(MarketplaceType type, EndpointKey key, long accountId, URI uri,
      Class<T> targetType) {
    return execute(type, key, accountId, uri, targetType, () ->
        streamingDownloadService.stream(
            uri, buildHeaders(type, accountId),
            retry(type, key), rateLimiter(type, key, accountId), bulkhead(type, key, accountId)
        ));
  }

  protected final <T> Flux<T> post(MarketplaceType type, EndpointKey key, long accountId, URI uri,
      Object body, Class<T> targetType) {
    return execute(type, key, accountId, uri, targetType, () ->
        streamingDownloadService.post(
            uri, buildHeaders(type, accountId), body,
            retry(type, key), rateLimiter(type, key, accountId), bulkhead(type, key, accountId)
        ));
  }

  private <T> Flux<T> execute(MarketplaceType type, EndpointKey key, long accountId, URI uri,
      Class<T> targetType,
      Supplier<Flux<DataBuffer>> call) {
    if (uri == null) {
      throw new AppException(MessageCodes.URI_REQUIRED);
    }
    if (targetType == null) {
      throw new AppException(MessageCodes.TYPE_REQUIRED);
    }

    Flux<DataBuffer> bytes = call.get()
        .onErrorMap(ex -> new MarketplaceExceptions.FetchFailed(ex,
            MessageCodes.MARKETPLACE_FETCH_FAILED, type, key.tag(), accountId, uri));

    return fluxReader.readArray(bytes, targetType)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(ex,
            MessageCodes.MARKETPLACE_PARSE_FAILED, type, key.tag(), accountId, uri));
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }

  private Bulkhead bulkhead(MarketplaceType type, EndpointKey key, long accountId) {
    return resilienceFactory.bulkhead(type, key.tag(), accountId);
  }

  private Retry retry(MarketplaceType type, EndpointKey key) {
    return resilienceFactory.retry(type, key.tag());
  }

  private RateLimiter rateLimiter(MarketplaceType type, EndpointKey key, long accountId) {
    return resilienceFactory.rateLimiter(type, key.tag(), accountId);
  }
}
