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
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class OzonAdapter extends AbstractReactiveMarketplaceAdapter implements MarketplaceAdapter {

  private static final MarketplaceType TYPE = MarketplaceType.OZON;
  private final EndpointsResolver endpoints;

  public OzonAdapter(EndpointsResolver endpoints,
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
    URI uri = endpoints.sales(TYPE);
    var body = Map.of("date_from", from.toString(), "date_to", to.toString());
    return post(TYPE, accountId, uri, body, SaleDto.class);
  }

  @Override
  public Flux<StockDto> fetchStock(long accountId, LocalDate onDate) {
    URI uri = endpoints.stock(TYPE);
    var body = Map.of("sku", new int[]{}); // заполни по контракту OZON
    return post(TYPE, accountId, uri, body, StockDto.class);
  }

  @Override
  public Flux<FinanceDto> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    URI uri = endpoints.finance(TYPE);
    var body = Map.of("date_from", from.toString(), "date_to", to.toString(), "page_size", 1000);
    return post(TYPE, accountId, uri, body, FinanceDto.class);
  }

  @Override
  public Flux<ReviewDto> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    URI uri = endpoints.reviews(TYPE);
    var body = Map.of("date_from", from.toString(), "date_to", to.toString());
    return post(TYPE, accountId, uri, body, ReviewDto.class);
  }
}
