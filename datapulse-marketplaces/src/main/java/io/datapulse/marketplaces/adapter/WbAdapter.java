package io.datapulse.marketplaces.adapter;

import io.datapulse.marketplaces.endpoints.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.resilience.ResilienceFactory;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.domain.dto.SaleDto;
import io.datapulse.domain.dto.StockDto;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

@Component
public class WbAdapter extends AbstractReactiveMarketplaceAdapter implements MarketplaceAdapter {

  private static final MarketplaceType TYPE = MarketplaceType.WILDBERRIES;
  private final EndpointsResolver endpoints;

  public WbAdapter(EndpointsResolver endpoints,
      StreamingDownloadService streamingDownloadService,
      ResilienceFactory resilienceFactory,
      JsonFluxReader fluxReader,
      HttpHeaderProvider headerProvider,
      CredentialsProvider credentialsProvider) {
    super(streamingDownloadService, resilienceFactory, fluxReader, headerProvider,
        credentialsProvider);
    this.endpoints = endpoints;
  }

  @Override
  public Flux<SaleDto> fetchSales(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.sales(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, accountId, uri, SaleDto.class);
  }

  @Override
  public Flux<StockDto> fetchStock(long accountId, LocalDate onDate) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.stock(TYPE))
        .queryParam("date", onDate)
        .build(true).toUri();
    return get(TYPE, accountId, uri, StockDto.class);
  }

  @Override
  public Flux<FinanceDto> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.finance(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, accountId, uri, FinanceDto.class);
  }

  @Override
  public Flux<ReviewDto> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.reviews(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, accountId, uri, ReviewDto.class);
  }
}
