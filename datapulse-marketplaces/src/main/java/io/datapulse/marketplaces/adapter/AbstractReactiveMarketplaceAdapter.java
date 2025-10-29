package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.MarketplaceExceptions;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.ResilienceManager;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * Base reactive adapter: apply RL/BH/Retry to raw HTTP stream BEFORE decoding.
 */
@Slf4j
abstract class AbstractReactiveMarketplaceAdapter {

  protected final StreamingDownloadService streamingDownloadService;
  protected final ResilienceManager resilienceManager;
  protected final JsonFluxReader fluxReader;
  protected final HttpHeaderProvider headerProvider;
  protected final CredentialsProvider credentialsProvider;

  protected AbstractReactiveMarketplaceAdapter(StreamingDownloadService streamingDownloadService,
      ResilienceManager resilienceManager,
      JsonFluxReader fluxReader,
      HttpHeaderProvider headerProvider,
      CredentialsProvider credentialsProvider) {
    this.streamingDownloadService = Objects.requireNonNull(streamingDownloadService,
        "streamingDownloadService");
    this.resilienceManager = Objects.requireNonNull(resilienceManager, "resilienceManager");
    this.fluxReader = Objects.requireNonNull(fluxReader, "fluxReader");
    this.headerProvider = Objects.requireNonNull(headerProvider, "headerProvider");
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
  }

  protected final <T> Flux<T> get(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Class<T> targetType) {
    HttpHeaders headers = buildHeaders(type, accountId);
    Flux<DataBuffer> raw = streamingDownloadService.stream(uri, headers);
    return execute(type, key, accountId, uri, targetType, raw);
  }

  protected final <T> Flux<T> post(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Object body,
      Class<T> targetType) {
    HttpHeaders headers = buildHeaders(type, accountId);
    Flux<DataBuffer> raw = streamingDownloadService.post(uri, headers, body);
    return execute(type, key, accountId, uri, targetType, raw);
  }

  private <T> Flux<T> execute(
      MarketplaceType type, EndpointKey key, long accountId, URI uri, Class<T> targetType,
      Flux<DataBuffer> raw) {
    if (uri == null) {
      throw new AppException(MessageCodes.URI_REQUIRED);
    }
    if (targetType == null) {
      throw new AppException(MessageCodes.TYPE_REQUIRED);
    }

    final String ctx = type + ":" + key.tag() + ":acc=" + accountId;

    Flux<DataBuffer> guarded = resilienceManager.apply(raw, type, key, accountId)
        .doOnSubscribe(s -> log.debug("[{}] start streaming {}", ctx, uri))
        .doFinally(st -> {
          if (st == SignalType.CANCEL) {
            log.debug("[{}] stream cancelled {}", ctx, uri);
          } else {
            log.debug("[{}] stream finalized={} {}", ctx, st, uri);
          }
        })
        .onErrorMap(ex -> {
          if (ex instanceof CancellationException || Exceptions.isCancel(ex)) {
            return ex;
          }
          return new MarketplaceExceptions.FetchFailed(
              ex, MessageCodes.MARKETPLACE_FETCH_FAILED, type, key.tag(), accountId, uri);
        });

    return fluxReader.readArray(guarded, targetType)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, type, key.tag(), accountId, uri));
  }

  private HttpHeaders buildHeaders(MarketplaceType type, long accountId) {
    var creds = credentialsProvider.resolve(accountId, type);
    return headerProvider.build(type, creds);
  }
}
