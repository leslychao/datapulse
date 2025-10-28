package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.resilience.ResilienceFactory;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.wb.WbFinanceRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbReviewRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbStockRaw;
import io.datapulse.marketplaces.endpoints.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

@Component
public class WbAdapter extends AbstractReactiveMarketplaceAdapter
    implements MarketplaceAdapter<WbSaleRaw, WbStockRaw, WbFinanceRaw, WbReviewRaw> {

  private static final MarketplaceType TYPE = MarketplaceType.WILDBERRIES;
  private final EndpointsResolver endpoints;

  public WbAdapter(
      EndpointsResolver endpoints,
      StreamingDownloadService streamingDownloadService,
      ResilienceFactory resilienceFactory,
      JsonFluxReader fluxReader,
      HttpHeaderProvider headerProvider,
      CredentialsProvider credentialsProvider) {
    super(
        streamingDownloadService,
        resilienceFactory,
        fluxReader,
        headerProvider,
        credentialsProvider);
    this.endpoints = endpoints;
  }

  @Override
  public MarketplaceType type() {
    return TYPE;
  }

  @Override
  public Flux<WbSaleRaw> fetchSales(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.sales(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, accountId, uri, WbSaleRaw.class);
  }

  @Override
  public Flux<WbStockRaw> fetchStock(long accountId, LocalDate onDate) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.stock(TYPE))
        .queryParam("date", onDate)
        .build(true).toUri();
    return get(TYPE, accountId, uri, WbStockRaw.class);
  }

  @Override
  public Flux<WbFinanceRaw> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.finance(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, accountId, uri, WbFinanceRaw.class);
  }

  @Override
  public Flux<WbReviewRaw> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.reviews(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, accountId, uri, WbReviewRaw.class);
  }
}
