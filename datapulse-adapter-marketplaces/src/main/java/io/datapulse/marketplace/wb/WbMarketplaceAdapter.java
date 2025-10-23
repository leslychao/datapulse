package io.datapulse.marketplace.wb;

import io.datapulse.domain.model.Sale;
import io.datapulse.domain.port.MarketplacePort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

@Slf4j
@Component("wbMarketplaceAdapter")
@RequiredArgsConstructor
public class WbMarketplaceAdapter implements MarketplacePort {

  private final WebClient webClient;
  private final WbEndpoints wbEndpoints;
  private final WbNormalizer wbNormalizer;

  @Override
  public List<Sale> fetchSales(Long accountId, LocalDate fromInclusive, LocalDate toInclusive) {
    String token = wbEndpoints.resolveTokenForAccount(accountId);

    var uri = wbEndpoints.sales(fromInclusive, toInclusive);
    var responseType = new ParameterizedTypeReference<List<Map<String, Object>>>() {
    };
    var raw = webClient.get()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.AUTHORIZATION, token)
        .retrieve()
        .bodyToMono(responseType)
        .retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(300)).jitter(0.3))
        .block();

    return wbNormalizer.normalizeSales(accountId, raw);
  }
}
