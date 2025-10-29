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
import io.datapulse.marketplaces.resilience.ResilienceManager;
import io.datapulse.marketplaces.resilience.ResilienceManager.ResilienceKit;
import java.net.URI;
import java.util.Objects;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;

abstract class AbstractReactiveMarketplaceAdapter {

  protected final StreamingDownloadService streamingDownloadService;
  protected final ResilienceManager resilienceManager;
  protected final JsonFluxReader fluxReader;
  protected final HttpHeaderProvider headerProvider;
  protected final CredentialsProvider credentialsProvider;

  protected AbstractReactiveMarketplaceAdapter(
      StreamingDownloadService streamingDownloadService,
      ResilienceManager resilienceManager,
      JsonFluxReader fluxReader,
      HttpHeaderProvider headerProvider,
      CredentialsProvider credentialsProvider
  ) {
    this.streamingDownloadService = Objects.requireNonNull(streamingDownloadService);
    this.resilienceManager = Objects.requireNonNull(resilienceManager);
    this.fluxReader = Objects.requireNonNull(fluxReader);
    this.headerProvider = Objects.requireNonNull(headerProvider);
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
  }

  protected final <T> Flux<T> get(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Class<T> targetType
  ) {
    HttpHeaders headers = buildHeaders(type, accountId);
    Flux<DataBuffer> raw = streamingDownloadService.stream(uri, headers);
    return execute(type, key, accountId, uri, targetType, raw);
  }

  protected final <T> Flux<T> post(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Object body, Class<T> targetType
  ) {
    HttpHeaders headers = buildHeaders(type, accountId);
    Flux<DataBuffer> raw = streamingDownloadService.post(uri, headers, body);
    return execute(type, key, accountId, uri, targetType, raw);
  }

  private <T> Flux<T> execute(
      MarketplaceType type,
      EndpointKey key,
      long accountId,
      URI uri,
      Class<T> targetType,
      Flux<DataBuffer> raw // ← вместо Supplier
  ) {
    if (uri == null) throw new AppException(MessageCodes.URI_REQUIRED);
    if (targetType == null) throw new AppException(MessageCodes.TYPE_REQUIRED);

    ResilienceKit kit = resilienceManager.kit(type, key.tag(), accountId);

    Flux<DataBuffer> guarded = resilienceManager
        .apply(raw, kit)            // Flux ленивый — выполнится только при subscribe
        .retryWhen(kit.retry())
        .onErrorMap(ex -> new MarketplaceExceptions.FetchFailed(
            ex, MessageCodes.MARKETPLACE_FETCH_FAILED, type, key.tag(), accountId, uri));

    return fluxReader.readArray(guarded, targetType)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, type, key.tag(), accountId, uri));
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }
}
