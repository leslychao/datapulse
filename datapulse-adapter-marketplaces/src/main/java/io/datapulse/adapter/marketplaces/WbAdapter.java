package io.datapulse.adapter.marketplaces;

import io.datapulse.adapter.marketplaces.endpoints.WbEndpoints;
import io.datapulse.adapter.marketplaces.http.HttpHeaderProvider;
import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.resilience.ResilienceFactory;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.domain.dto.SaleDto;
import io.datapulse.domain.dto.StockDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.MarketplaceExceptions;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.net.URI;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class WbAdapter implements MarketplaceAdapter {

  private static final MarketplaceType TYPE = MarketplaceType.WILDBERRIES;

  private final StreamingDownloadService streamingDownloadService;
  private final ResilienceFactory resilienceFactory;
  private final MarketplaceProperties props;
  private final JsonFluxReader fluxReader;
  private final HttpHeaderProvider headerProvider;
  private final CredentialsProvider credentialsProvider;

  @Override
  public Flux<SaleDto> fetchSales(long accountId, LocalDate from, LocalDate to) {
    var uri = WbEndpoints.sales(props.get(TYPE), from, to);
    return doFetch(accountId, uri, SaleDto.class);
  }

  @Override
  public Flux<StockDto> fetchStock(long accountId, LocalDate onDate) {
    var uri = WbEndpoints.stock(props.get(TYPE), onDate);
    return doFetch(accountId, uri, StockDto.class);
  }

  @Override
  public Flux<FinanceDto> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    var uri = WbEndpoints.finance(props.get(TYPE), from, to);
    return doFetch(accountId, uri, FinanceDto.class);
  }

  @Override
  public Flux<ReviewDto> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    var uri = WbEndpoints.reviews(props.get(TYPE), from, to);
    return doFetch(accountId, uri, ReviewDto.class);
  }

  private HttpHeaders auth(long accountId) {
    var creds = credentialsProvider.resolve(accountId, TYPE);
    return headerProvider.build(TYPE, creds);
  }

  private <T> Flux<T> doFetch(long accountId, URI uri, Class<T> type) {
    if (uri == null) {
      throw new AppException(MessageCodes.VALIDATION_URI_REQUIRED);
    }
    if (type == null) {
      throw new AppException(MessageCodes.VALIDATION_TYPE_REQUIRED);
    }

    Bulkhead bulkhead = resilienceFactory.bulkhead(TYPE);
    Retry retry = resilienceFactory.retry(TYPE);
    RateLimiter rateLimiter = resilienceFactory.rateLimiter(TYPE);
    HttpHeaders headers = auth(accountId);

    var segments = UriComponentsBuilder.fromUri(uri).build().getPathSegments();
    String endpoint = segments.isEmpty() ? "unknown" : segments.get(segments.size() - 1);

    Flux<DataBuffer> bytes = streamingDownloadService
        .stream(uri, headers, retry, rateLimiter, bulkhead)
        .onErrorMap(ex -> new MarketplaceExceptions.FetchFailed(
            ex, MessageCodes.MARKETPLACE_FETCH_FAILED, TYPE, endpoint, accountId, uri));

    return fluxReader.readArray(bytes, type)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, TYPE, endpoint, accountId, uri));
  }
}
