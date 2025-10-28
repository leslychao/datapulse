package io.datapulse.adapter.marketplaces;

import io.datapulse.adapter.marketplaces.endpoints.WbEndpoints;
import io.datapulse.adapter.marketplaces.http.HttpHeaderProvider;
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
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class WbAdapter implements MarketplaceAdapter {

  private static final MarketplaceType MARKETPLACE_TYPE = MarketplaceType.WILDBERRIES;

  private final WbEndpoints endpoints;
  private final StreamingDownloadService streamingDownloadService;
  private final ResilienceFactory resilienceFactory;
  private final JsonFluxReader fluxReader;
  private final HttpHeaderProvider headerProvider;
  private final CredentialsProvider credentialsProvider;

  @Override
  public Flux<SaleDto> fetchSales(long accountId, LocalDate from, LocalDate to) {
    return doFetch(accountId, endpoints.sales(from, to), "sales", SaleDto.class);
  }

  @Override
  public Flux<StockDto> fetchStock(long accountId, LocalDate onDate) {
    return doFetch(accountId, endpoints.stock(onDate), "stock", StockDto.class);
  }

  @Override
  public Flux<FinanceDto> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    return doFetch(accountId, endpoints.finance(from, to), "finance", FinanceDto.class);
  }

  @Override
  public Flux<ReviewDto> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    return doFetch(accountId, endpoints.reviews(from, to), "reviews", ReviewDto.class);
  }

  private HttpHeaders buildHeaders(long accountId) {
    var creds = credentialsProvider.resolve(accountId, MARKETPLACE_TYPE);
    return headerProvider.build(MARKETPLACE_TYPE, creds);
  }

  private <T> Flux<T> doFetch(long accountId, URI uri, String endpoint, Class<T> type) {
    if (uri == null) {
      throw new AppException(MessageCodes.URI_REQUIRED);
    }
    if (type == null) {
      throw new AppException(MessageCodes.TYPE_REQUIRED);
    }

    Bulkhead bulkhead = resilienceFactory.bulkhead(MARKETPLACE_TYPE);
    Retry retry = resilienceFactory.retry(MARKETPLACE_TYPE);
    RateLimiter rateLimiter = resilienceFactory.rateLimiter(MARKETPLACE_TYPE);
    HttpHeaders headers = buildHeaders(accountId);

    Flux<DataBuffer> dataBufferFlux = streamingDownloadService
        .stream(uri, headers, retry, rateLimiter, bulkhead)
        .onErrorMap(ex -> new MarketplaceExceptions.FetchFailed(
            ex, MessageCodes.MARKETPLACE_FETCH_FAILED, MARKETPLACE_TYPE, endpoint, accountId, uri));

    return fluxReader.readArray(dataBufferFlux, type)
        .onErrorMap(ex -> new MarketplaceExceptions.ParseFailed(
            ex, MessageCodes.MARKETPLACE_PARSE_FAILED, MARKETPLACE_TYPE, endpoint, accountId, uri));
  }
}
