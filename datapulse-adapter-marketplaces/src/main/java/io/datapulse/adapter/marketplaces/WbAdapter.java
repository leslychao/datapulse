package io.datapulse.adapter.marketplaces;

import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.resilience.ResilienceFactory;
import io.datapulse.core.service.StreamingDownloadService;

import io.datapulse.domain.dto.SaleDto;
import java.net.URI;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;


@Component
@RequiredArgsConstructor
public class WbAdapter implements MarketplaceAdapter {

  private static final String PROVIDER_KEY = "wb";

  private final StreamingDownloadService downloader; // возвращает Flux<DataBuffer>
  private final ResilienceFactory resilienceFactory;
  private final MarketplaceProperties props;
  private final JsonFluxReader json;

  private MarketplaceProperties.Provider cfg() {
    var p = props.getProviders().get(PROVIDER_KEY);
    if (p == null) throw new IllegalStateException("WB provider config is missing");
    return p;
  }

  private HttpHeaders auth(long accountId) {
    HttpHeaders h = new HttpHeaders();
    h.set("Authorization", WbTokenService.bearerFor(accountId)); // твой сервис/хранилище токенов
    h.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
    return h;
  }

  @Override
  public Flux<SaleDto> fetchSales(long accountId, LocalDate from, LocalDate to) {
    var p   = cfg();
    var uri = URI.create(p.getBaseUrl() + p.getEndpoints().getSales()
        + "?dateFrom=" + from + "&dateTo=" + to);

    ResilienceProfile r = resilienceFactory.create(PROVIDER_KEY);

    Flux<DataBuffer> bytes = downloader.stream(uri, auth(accountId), r.retry(), r.rateLimiter(), r.bulkhead());
    // Если ответ — обёрнутый объект, замени на readArrayAt(bytes, SaleDto.class, "/result/items")
    return json.readArray(bytes, SaleDto.class)
        .map(d -> { d.setAccountId(accountId); return d; });
  }

  @Override
  public Flux<StockDto> fetchStock(long accountId, LocalDate onDate) {
    var p   = cfg();
    var uri = URI.create(p.getBaseUrl() + p.getEndpoints().getStock()
        + "?date=" + onDate);

    ResilienceProfile r = resilienceFactory.create(PROVIDER_KEY);

    Flux<DataBuffer> bytes = downloader.stream(uri, auth(accountId), r.retry(), r.rateLimiter(), r.bulkhead());
    return json.readArray(bytes, StockDto.class)
        .map(d -> { d.setAccountId(accountId); return d; });
  }

  @Override
  public Flux<FinanceDto> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    var p   = cfg();
    var uri = URI.create(p.getBaseUrl() + p.getEndpoints().getFinance()
        + "?dateFrom=" + from + "&dateTo=" + to);

    ResilienceProfile r = resilienceFactory.create(PROVIDER_KEY);

    Flux<DataBuffer> bytes = downloader.stream(uri, auth(accountId), r.retry(), r.rateLimiter(), r.bulkhead());
    return json.readArray(bytes, FinanceDto.class)
        .map(d -> { d.setAccountId(accountId); return d; });
  }

  @Override
  public Flux<ReviewDto> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    var p   = cfg();
    var uri = URI.create(p.getBaseUrl() + p.getEndpoints().getReviews()
        + "?dateFrom=" + from + "&dateTo=" + to);

    ResilienceProfile r = resilienceFactory.create(PROVIDER_KEY);

    Flux<DataBuffer> bytes = downloader.stream(uri, auth(accountId), r.retry(), r.rateLimiter(), r.bulkhead());
    return json.readArray(bytes, ReviewDto.class)
        .map(d -> { d.setAccountId(accountId); return d; });
  }
}
