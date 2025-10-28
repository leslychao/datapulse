package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.resilience.ResilienceFactory;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.ozon.OzonFinanceRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonReviewRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonSaleRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonStockRaw;
import io.datapulse.marketplaces.endpoints.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class OzonAdapter extends AbstractReactiveMarketplaceAdapter
    implements MarketplaceAdapter<OzonSaleRaw, OzonStockRaw, OzonFinanceRaw, OzonReviewRaw> {

  private static final MarketplaceType TYPE = MarketplaceType.OZON;
  private final EndpointsResolver endpoints;

  public OzonAdapter(
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
  public Flux<OzonSaleRaw> fetchSales(long accountId, LocalDate from, LocalDate to) {
    URI uri = endpoints.sales(TYPE);
    var body = Map.of("date_from", from.toString(), "date_to", to.toString());
    return post(TYPE, accountId, uri, body, OzonSaleRaw.class);
  }

  @Override
  public Flux<OzonStockRaw> fetchStock(long accountId, LocalDate onDate) {
    URI uri = endpoints.stock(TYPE);
    var body = Map.of("sku", new int[]{}, "limit", 1000, "offset", 0);
    return post(TYPE, accountId, uri, body, OzonStockRaw.class);
  }

  @Override
  public Flux<OzonFinanceRaw> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    URI uri = endpoints.finance(TYPE);
    var body = Map.of("date_from", from.toString(), "date_to", to.toString(), "page_size", 1000);
    return post(TYPE, accountId, uri, body, OzonFinanceRaw.class);
  }

  @Override
  public Flux<OzonReviewRaw> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    URI uri = endpoints.reviews(TYPE);
    var body = Map.of("date_from", from.toString(), "date_to", to.toString());
    return post(TYPE, accountId, uri, body, OzonReviewRaw.class);
  }
}
