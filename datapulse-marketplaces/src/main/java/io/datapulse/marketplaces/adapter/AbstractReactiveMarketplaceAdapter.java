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
import java.nio.file.Paths;
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
    this.streamingDownloadService = Objects.requireNonNull(streamingDownloadService,
        "streamingDownloadService");
    this.resilienceFactory = Objects.requireNonNull(resilienceFactory, "resilienceFactory");
    this.fluxReader = Objects.requireNonNull(fluxReader, "fluxReader");
    this.headerProvider = Objects.requireNonNull(headerProvider, "headerProvider");
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
  }

  protected final <T> Flux<T> get(MarketplaceType type, long accountId, URI uri,
      Class<T> targetType) {
    return execute(type, accountId, uri, targetType,
        () -> streamingDownloadService.stream(
            uri,
            buildHeaders(type, accountId),
            retry(type),
            rateLimiter(type, accountId),
            bulkhead(type, accountId)
        ));
  }

  protected final <T> Flux<T> post(MarketplaceType type, long accountId, URI uri, Object body,
      Class<T> targetType) {
    return execute(type, accountId, uri, targetType,
        () -> streamingDownloadService.post(
            uri,
            buildHeaders(type, accountId),
            body,
            retry(type),
            rateLimiter(type, accountId),
            bulkhead(type, accountId)
        ));
  }

  private <T> Flux<T> execute(
      MarketplaceType type, long accountId, URI uri, Class<T> targetType,
      Supplier<Flux<DataBuffer>> call
  ) {
    if (uri == null) {
      throw new AppException(MessageCodes.URI_REQUIRED);
    }
    if (targetType == null) {
      throw new AppException(MessageCodes.TYPE_REQUIRED);
    }

    final String endpoint = endpointOf(uri);

    Flux<DataBuffer> bytes = call.get()
        .onErrorMap(
            ex -> new MarketplaceExceptions.FetchFailed(ex, MessageCodes.MARKETPLACE_FETCH_FAILED,
                type, endpoint, accountId, uri));

    return fluxReader.readArray(bytes, targetType)
        .onErrorMap(
            ex -> new MarketplaceExceptions.ParseFailed(ex, MessageCodes.MARKETPLACE_PARSE_FAILED,
                type, endpoint, accountId, uri));
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }

  private static String endpointOf(URI uri) {
    String p = (uri.getRawPath() != null) ? uri.getRawPath() : uri.getPath();
    if (p == null || p.isBlank()) {
      return "unknown";
    }
    if (p.startsWith("/")) {
      p = p.substring(1);
    }
    if (p.endsWith("/")) {
      p = p.substring(0, p.length() - 1);
    }
    return Paths.get(p).normalize().toString();
  }

  private Bulkhead bulkhead(MarketplaceType type, long accountId) {
    return resilienceFactory.bulkhead(type, accountId);
  }

  private Retry retry(MarketplaceType type) {
    return resilienceFactory.retry(type);
  }

  private RateLimiter rateLimiter(MarketplaceType type, long accountId) {
    return resilienceFactory.rateLimiter(type, accountId);
  }
}
