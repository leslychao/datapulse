package io.datapulse.marketplaces.adapter;

import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.resilience.ResilienceFactory;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.MarketplaceExceptions;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.net.URI;
import java.nio.file.Paths;
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
    this.streamingDownloadService = streamingDownloadService;
    this.resilienceFactory = resilienceFactory;
    this.fluxReader = fluxReader;
    this.headerProvider = headerProvider;
    this.credentialsProvider = credentialsProvider;
  }

  protected <T> Flux<T> get(MarketplaceType type, long accountId, URI uri, Class<T> targetType) {
    require(uri, targetType);
    String endpoint = extractEndpoint(uri);

    Bulkhead bulkhead = resilienceFactory.bulkhead(type);
    Retry retry = resilienceFactory.retry(type);
    RateLimiter rateLimiter = resilienceFactory.rateLimiter(type);
    HttpHeaders headers = buildHeaders(type, accountId);

    Flux<DataBuffer> body = streamingDownloadService
        .stream(uri, headers, retry, rateLimiter, bulkhead)
        .onErrorMap(ex -> new MarketplaceExceptions.FetchFailed(
            ex, MessageCodes.MARKETPLACE_FETCH_FAILED, type, endpoint, accountId, uri));

    return fluxReader.readArray(body, targetType)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, type, endpoint, accountId, uri));
  }

  protected <T> Flux<T> post(MarketplaceType type, long accountId, URI uri, Object requestBody,
      Class<T> targetType) {
    require(uri, targetType);
    String endpoint = extractEndpoint(uri);

    Bulkhead bulkhead = resilienceFactory.bulkhead(type);
    Retry retry = resilienceFactory.retry(type);
    RateLimiter rateLimiter = resilienceFactory.rateLimiter(type);
    HttpHeaders headers = buildHeaders(type, accountId);

    Flux<DataBuffer> body = streamingDownloadService
        .post(uri, headers, requestBody, retry, rateLimiter, bulkhead)
        .onErrorMap(ex -> new MarketplaceExceptions.FetchFailed(
            ex, MessageCodes.MARKETPLACE_FETCH_FAILED, type, endpoint, accountId, uri));

    return fluxReader.readArray(body, targetType)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, type, endpoint, accountId, uri));
  }

  // ===== helpers =====
  private static void require(URI uri, Class<?> type) {
    if (uri == null) {
      throw new AppException(MessageCodes.URI_REQUIRED);
    }
    if (type == null) {
      throw new AppException(MessageCodes.TYPE_REQUIRED);
    }
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }

  protected static String extractEndpoint(URI uri) {
    String path = uri.getPath();
    if (path == null || path.isBlank()) {
      return "unknown";
    }
    path = path.startsWith("/") ? path.substring(1) : path;
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return Paths.get(path).normalize().toString();
  }
}
