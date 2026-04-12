package io.datapulse.execution.adapter.yandex;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.execution.adapter.yandex.dto.YandexPriceOffer;
import io.datapulse.execution.adapter.yandex.dto.YandexPriceValue;
import io.datapulse.execution.adapter.yandex.dto.YandexUpdatePricesRequest;
import io.datapulse.execution.adapter.yandex.dto.YandexUpdatePricesResponse;
import io.datapulse.execution.domain.PriceWriteAdapter;
import io.datapulse.execution.domain.PriceWriteResult;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Yandex Market price write: synchronous POST → blanket OK/ERROR.
 *
 * <p>Unlike Ozon (per-item updated/errors), Yandex returns only
 * {@code {"status": "OK"}} without per-item granularity (write-contracts.md F-7).
 *
 * <p>Price unit: BigDecimal (rubles with kopecks). NOT integer like WB.
 *
 * <p>Rate limit HTTP code is <b>420</b> (not 429) — handled by ErrorClassifier.
 *
 * <p>MVP reconciliation: Write → status OK → immediately SUCCEEDED.
 * No read-after-write or quarantine check in this phase.
 *
 * <p>TODO Phase 2: Quarantine check + deferred reconciliation.
 * POST /v2/businesses/{businessId}/price-quarantine — check if quarantined.
 * POST /v2/businesses/{businessId}/price-quarantine/confirm — confirm quarantine.
 * If quarantined → RECONCILIATION_PENDING until quarantine resolves.
 * Wait 30-60s → read back via offer-prices → compare → quarantine check.
 * Yandex: "данные обновляются не мгновенно" — need delay before read-back.
 * See write-contracts.md §3.2 for full reconciliation flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexPriceWriteAdapter implements PriceWriteAdapter {

  private static final String PRICE_UPDATE_PATH =
      "/v2/businesses/%d/offer-prices/updates";
  private static final String CURRENCY_RUR = "RUR";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties integrationProperties;
  private final MarketplaceRateLimiter rateLimiter;
  private final ObjectMapper objectMapper;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public PriceWriteResult setPrice(long connectionId, String marketplaceSku,
      BigDecimal targetPrice, Map<String, String> credentials) {
    String baseUrl = integrationProperties.getYandex().getWriteBaseUrl();
    String apiKey = credentials.get(CredentialKeys.YANDEX_API_KEY);
    long businessId = extractBusinessId(credentials);

    String endpoint = baseUrl + PRICE_UPDATE_PATH.formatted(businessId);

    var request = new YandexUpdatePricesRequest(List.of(
        new YandexPriceOffer(
            marketplaceSku,
            new YandexPriceValue(targetPrice, CURRENCY_RUR)
        )
    ));

    String requestSummary = serialize(Map.of(
        "endpoint", endpoint,
        "offerId", marketplaceSku,
        "targetPrice", targetPrice,
        "businessId", businessId
    ));

    rateLimiter.acquire(connectionId, RateLimitGroup.YANDEX_PRICE_UPDATE).join();

    try {
      String responseBody = webClientBuilder.build()
          .post()
          .uri(endpoint)
          .header("Api-Key", apiKey)
          .bodyValue(request)
          .retrieve()
          .bodyToMono(String.class)
          .block();

      rateLimiter.onResponse(
          connectionId, RateLimitGroup.YANDEX_PRICE_UPDATE, 200);

      return parseResponse(requestSummary, responseBody);
    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          connectionId, RateLimitGroup.YANDEX_PRICE_UPDATE, status);
      throw e;
    }
  }

  private PriceWriteResult parseResponse(String requestSummary,
      String responseBody) {
    try {
      var response = objectMapper.readValue(
          responseBody, YandexUpdatePricesResponse.class);

      if (response.isOk()) {
        return PriceWriteResult.confirmed(requestSummary, responseBody);
      }

      return PriceWriteResult.rejected(
          requestSummary, responseBody,
          "YANDEX_STATUS_ERROR",
          "Yandex returned status: " + response.status());
    } catch (Exception e) {
      log.warn("Failed to parse Yandex price update response, "
          + "treating as uncertain: {}", e.getMessage());
      return PriceWriteResult.uncertain(requestSummary, responseBody);
    }
  }

  /**
   * Extracts businessId from credentials map.
   * {@link ExecutionCredentialResolver} enriches credentials with businessId
   * parsed from connection metadata (populated by YandexHealthProbe).
   */
  private long extractBusinessId(Map<String, String> credentials) {
    String value = credentials.get(CredentialKeys.YANDEX_BUSINESS_ID);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Yandex businessId not found in credentials — "
              + "connection metadata may be missing");
    }
    return Long.parseLong(value);
  }

  private String serialize(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return obj.toString();
    }
  }
}
