package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.wb.WbFinanceRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbReviewRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbStockRaw;
import io.datapulse.marketplaces.endpoints.EndpointKey;
import io.datapulse.marketplaces.endpoints.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.ResilienceManager;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

@Component
public class WbAdapter extends AbstractReactiveMarketplaceAdapter
    implements MarketplaceAdapter<WbSaleRaw, WbStockRaw, WbFinanceRaw, WbReviewRaw> {

  private static final MarketplaceType TYPE = MarketplaceType.WILDBERRIES;
  private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
  private final EndpointsResolver endpoints;

  public WbAdapter(EndpointsResolver endpoints,
      StreamingDownloadService s, ResilienceManager r, JsonFluxReader f,
      HttpHeaderProvider h, CredentialsProvider c) {
    super(s, r, f, h, c);
    this.endpoints = endpoints;
  }

  @Override
  public Flux<WbSaleRaw> fetchSales(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.salesRef(TYPE).uri())
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, EndpointKey.SALES, accountId, uri, WbSaleRaw.class);
  }

  @Override
  public Flux<WbStockRaw> fetchStock(long accountId, LocalDate onDate) {
    return get(TYPE, EndpointKey.STOCK, accountId, endpoints.stockRef(TYPE).uri(),
        WbStockRaw.class);
  }

  @Override
  public Flux<WbFinanceRaw> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    URI uri = UriComponentsBuilder.fromUri(endpoints.financeRef(TYPE).uri())
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .queryParam("limit", 100000)
        .build(true).toUri();
    return get(TYPE, EndpointKey.FINANCE, accountId, uri, WbFinanceRaw.class);
  }

  @Override
  public Flux<WbReviewRaw> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    long fromSec = from.atStartOfDay(MSK).toEpochSecond();
    long toSec = to.atTime(23, 59, 59).atZone(MSK).toEpochSecond();
    URI uri = UriComponentsBuilder.fromUri(endpoints.reviewsRef(TYPE).uri())
        .queryParam("isAnswered", false).queryParam("take", 1000).queryParam("skip", 0)
        .queryParam("dateFrom", fromSec).queryParam("dateTo", toSec)
        .build(true).toUri();
    return get(TYPE, EndpointKey.REVIEWS, accountId, uri, WbReviewRaw.class);
  }
}
