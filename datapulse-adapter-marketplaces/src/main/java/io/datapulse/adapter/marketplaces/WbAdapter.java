package io.datapulse.adapter.marketplaces.wb;


import io.datapulse.adapter.marketplaces.MarketplaceAdapter;
import io.datapulse.adapter.marketplaces.support.MarketplaceExceptions;
import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.core.parser.JsonFluxReader;
import io.datapulse.core.resilience.ResilienceFactory;
import io.datapulse.core.service.StreamingDownloadService;
import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.domain.dto.SaleDto;
import io.datapulse.domain.dto.StockDto;
import io.datapulse.marketplaces.wb.WbEndpoints;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.net.URI;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class WbAdapter implements MarketplaceAdapter {

  private static final String PROVIDER_KEY = "wb";

  private final StreamingDownloadService streamingDownloadService;       // абстракция транспорта + Resilience4j
  private final ResilienceFactory resilienceFactory; // ваша фабрика профилей
  private final MarketplaceProperties props;      // конфигурация провайдеров
  private final JsonFluxReader fluxReader;

  private MarketplaceProperties.Provider cfg() {
    var p = props.getProviders().get(PROVIDER_KEY);
    if (p == null) {
      throw new MarketplaceExceptions.ConfigMissing("Конфигурация провайдера WB отсутствует");
    }
    return p;
  }

  @Override
  public Flux<SaleDto> fetchSales(long accountId, LocalDate from, LocalDate to) {
    var p = cfg();
    var uri = WbEndpoints.sales(p.getBaseUrl(), from, to);
    return doFetch(accountId, "sales", uri, SaleDto.class /*jsonPointer*/);
  }

  @Override
  public Flux<StockDto> fetchStock(long accountId, LocalDate onDate) {
    var p = cfg();
    var uri = WbEndpoints.stock(p.getBaseUrl(), onDate);
    return doFetch(accountId, "stock", uri, StockDto.class);
  }

  @Override
  public Flux<FinanceDto> fetchFinance(long accountId, LocalDate from, LocalDate to) {
    var p = cfg();
    var uri = WbEndpoints.finance(p.getBaseUrl(), from, to);
    return doFetch(accountId, "finance", uri, FinanceDto.class);
  }

  @Override
  public Flux<ReviewDto> fetchReviews(long accountId, LocalDate from, LocalDate to) {
    var p = cfg();
    var uri = WbEndpoints.reviews(p.getBaseUrl(), from, to);
    return doFetch(accountId, "reviews", uri, ReviewDto.class  /* "/result/items" если обёртка */);
  }

  private HttpHeaders auth(long accountId) {
    HttpHeaders h = new HttpHeaders();
    h.set("Authorization", "***"); // твой сервис/хранилище токенов
    h.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
    return h;
  }

  private <T> Flux<T> doFetch(long accountId, String endpoint, URI uri, Class<T> type) {
    Bulkhead bulkhead = resilienceFactory.bulkhead(PROVIDER_KEY);
    Retry retry = resilienceFactory.retry(PROVIDER_KEY);
    RateLimiter rateLimiter = resilienceFactory.rateLimiter(PROVIDER_KEY);

    Flux<DataBuffer> bytes = streamingDownloadService.stream(uri, new HttpHeaders(), retry,
        rateLimiter, bulkhead);

    return fluxReader.readArray(bytes, type);
  }
}
