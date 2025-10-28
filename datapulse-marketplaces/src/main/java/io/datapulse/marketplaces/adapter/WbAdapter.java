package io.datapulse.marketplaces.adapter;

import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.service.CredentialsProvider;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.wb.WbFinanceRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbReviewRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbStockRaw;
import io.datapulse.marketplaces.endpoints.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.resilience.ResilienceFactory;
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

  public WbAdapter(
      EndpointsResolver endpoints,
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
  public Flux<WbSaleRaw> fetchSales(long accountId, LocalDate from, LocalDate to) {
    // /api/v1/supplier/sales — dateFrom (YYYY-MM-DD), dateTo (YYYY-MM-DD)
    URI uri = UriComponentsBuilder.fromUri(endpoints.sales(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true).toUri();
    return get(TYPE, accountId, uri, WbSaleRaw.class);
  }

  @Override
  public Flux<WbStockRaw> fetchStock(long accountId, LocalDate onDate) {
    // /api/v1/supplier/stocks — БЕЗ параметров даты (остатки «на сейчас»)
    URI uri = endpoints.stock(TYPE);
    return get(TYPE, accountId, uri, WbStockRaw.class);
  }

  @Override
  public Flux<WbFinanceRaw> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    // /api/supplier/reportDetailByPeriod — dateFrom/dateTo (+ опц.: limit/rrdid)
    URI uri = UriComponentsBuilder.fromUri(endpoints.finance(TYPE))
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .queryParam("limit", 100000)   // WB default/max
        .build(true).toUri();
    return get(TYPE, accountId, uri, WbFinanceRaw.class);
  }

  @Override
  public Flux<WbReviewRaw> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    // /api/v1/questions — isAnswered, take, skip обязательны; dateFrom/dateTo — unix seconds (опционально)
    long fromSec = from.atStartOfDay(MSK).toEpochSecond();
    long toSec = to.atTime(23, 59, 59).atZone(MSK).toEpochSecond();

    URI uri = UriComponentsBuilder.fromUri(endpoints.reviews(TYPE))
        .queryParam("isAnswered", false)
        .queryParam("take", 1000)
        .queryParam("skip", 0)
        .queryParam("dateFrom", fromSec)
        .queryParam("dateTo", toSec)
        .build(true).toUri();
    return get(TYPE, accountId, uri, WbReviewRaw.class);
  }
}
