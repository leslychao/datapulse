package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.wb.WbFinanceRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbReviewRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbStockRaw;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.event.BusinessEvent;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.ResilienceManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class WbAdapter extends AbstractReactiveMarketplaceAdapter
    implements MarketplaceAdapter<WbSaleRaw, WbStockRaw, WbFinanceRaw, WbReviewRaw> {

  private static final MarketplaceType TYPE = MarketplaceType.WILDBERRIES;
  private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

  private final EndpointsResolver endpoints;

  public WbAdapter(EndpointsResolver endpoints,
      StreamingDownloadService s,
      ResilienceManager r,
      JsonFluxReader f,
      HttpHeaderProvider h,
      CredentialsProvider c) {
    super(s, r, f, h, c);
    this.endpoints = endpoints;
  }

  @Override
  public Flux<WbSaleRaw> fetchSales(long accountId, LocalDate from, LocalDate to) {
    var refs = endpoints.resolveAll(TYPE, BusinessEvent.SALES_FACT,
        Map.of("dateFrom", from, "dateTo", to));
    return mergeGet(TYPE, accountId, refs, WbSaleRaw.class);
  }

  @Override
  public Flux<WbStockRaw> fetchStock(long accountId, LocalDate onDate) {
    var refs = endpoints.resolveAll(TYPE, BusinessEvent.STOCK_LEVEL);
    return mergeGet(TYPE, accountId, refs, WbStockRaw.class);
  }

  @Override
  public Flux<WbFinanceRaw> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    var refs = endpoints.resolveAll(TYPE, BusinessEvent.RETURN,
        Map.of("dateFrom", from, "dateTo", to, "limit", 100_000));
    return mergeGet(TYPE, accountId, refs, WbFinanceRaw.class);
  }

  @Override
  public Flux<WbReviewRaw> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    long fromSec = from.atStartOfDay(MSK).toEpochSecond();
    long toSec = to.atTime(23, 59, 59).atZone(MSK).toEpochSecond();
    var refs = endpoints.resolveAll(TYPE, BusinessEvent.REVIEW,
        Map.of("isAnswered", false, "take", 1000, "skip", 0, "dateFrom", fromSec, "dateTo", toSec));
    return mergeGet(TYPE, accountId, refs, WbReviewRaw.class);
  }

}
