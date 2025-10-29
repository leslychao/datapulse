package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.ozon.OzonFinanceRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonReviewRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonSaleRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonStockRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointRef;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.event.BusinessEvent;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.ResilienceManager;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class OzonAdapter extends AbstractReactiveMarketplaceAdapter
    implements MarketplaceAdapter<OzonSaleRaw, OzonStockRaw, OzonFinanceRaw, OzonReviewRaw> {

  private static final MarketplaceType TYPE = MarketplaceType.OZON;
  private final EndpointsResolver endpoints;

  public OzonAdapter(EndpointsResolver endpoints,
      StreamingDownloadService s,
      ResilienceManager r,
      JsonFluxReader f,
      HttpHeaderProvider h,
      CredentialsProvider c) {
    super(s, r, f, h, c);
    this.endpoints = endpoints;
  }

  @Override
  public Flux<OzonSaleRaw> fetchSales(long accountId, LocalDate from, LocalDate to) {
    EndpointRef ref = endpoints.resolve(TYPE, BusinessEvent.SALES_FACT);
    URI uri = ref.uri();
    EndpointKey key = ref.key();

    var body = Map.of(
        "date_from", from.toString(),
        "date_to", to.toString(),
        "dimension", List.of("sku"),
        "metrics", List.of("revenue", "orders"),
        "filters", List.of()
    );
    return post(TYPE, key, accountId, uri, body, OzonSaleRaw.class);
  }

  @Override
  public Flux<OzonStockRaw> fetchStock(long accountId, LocalDate onDate) {
    EndpointRef ref = endpoints.resolve(TYPE, BusinessEvent.STOCK_LEVEL);
    URI uri = ref.uri();
    EndpointKey key = ref.key();

    var body = Map.of(
        "filter", Map.of("offer_id", List.of(), "product_id", List.of(), "sku", List.of()),
        "last_id", "",
        "limit", 1000
    );
    return post(TYPE, key, accountId, uri, body, OzonStockRaw.class);
  }

  @Override
  public Flux<OzonFinanceRaw> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    EndpointRef ref = endpoints.resolve(TYPE, BusinessEvent.RETURN); // temp → FINANCE per mapping
    URI uri = ref.uri();
    EndpointKey key = ref.key();

    var body = Map.of(
        "filter", Map.of("date", Map.of("from", from.toString(), "to", to.toString())),
        "page", Map.of("page", 1, "page_size", 1000)
    );
    return post(TYPE, key, accountId, uri, body, OzonFinanceRaw.class);
  }

  @Override
  public Flux<OzonReviewRaw> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    EndpointRef ref = endpoints.resolve(TYPE, BusinessEvent.REVIEW);
    URI uri = ref.uri();
    EndpointKey key = ref.key();

    var body = Map.of(
        "page", 1, "page_size", 100,
        "filter", Map.of("date_created", Map.of(
            "time_from", from + "T00:00:00Z",
            "time_to",   to   + "T23:59:59Z"))
    );
    return post(TYPE, key, accountId, uri, body, OzonReviewRaw.class);
  }
}
