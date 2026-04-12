package io.datapulse.execution.adapter.yandex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.execution.domain.PriceReadAdapter;
import io.datapulse.execution.domain.PriceReadResult;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Yandex Market price read for reconciliation:
 * POST /v2/businesses/{businessId}/offer-prices
 *
 * <p>Returns prices currently set via API. Used for read-after-write verification.
 *
 * <p>NOTE: Yandex data update is <b>not instantaneous</b> — takes up to several minutes.
 * For MVP, read-after-write happens immediately and may see stale data.
 * Phase 2: add 30-60s delay before reconciliation read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexPriceReadAdapter implements PriceReadAdapter {

  private static final String OFFER_PRICES_PATH =
      "/v2/businesses/%d/offer-prices";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties integrationProperties;
  private final MarketplaceRateLimiter rateLimiter;
  private final ObjectMapper objectMapper;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public PriceReadResult readCurrentPrice(long connectionId,
      String marketplaceSku, Map<String, String> credentials) {
    String baseUrl = integrationProperties.getYandex().getBaseUrl();
    String apiKey = credentials.get(CredentialKeys.YANDEX_API_KEY);
    long businessId = extractBusinessId(credentials);

    String endpoint = baseUrl + OFFER_PRICES_PATH.formatted(businessId);

    rateLimiter.acquire(connectionId, RateLimitGroup.YANDEX_CATALOG).join();

    String responseBody = webClientBuilder.build()
        .post()
        .uri(endpoint)
        .header("Api-Key", apiKey)
        .bodyValue(Map.of())
        .retrieve()
        .bodyToMono(String.class)
        .block();

    rateLimiter.onResponse(connectionId, RateLimitGroup.YANDEX_CATALOG, 200);

    return parseOfferPrices(responseBody, marketplaceSku, connectionId);
  }

  private PriceReadResult parseOfferPrices(String responseBody,
      String targetOfferId, long connectionId) {
    JsonNode root = parseJson(responseBody);
    JsonNode offers = root.path("result").path("offers");

    if (!offers.isArray()) {
      log.warn("Yandex price read: no offers in response, connectionId={}",
          connectionId);
      return new PriceReadResult(null, responseBody);
    }

    for (JsonNode offer : offers) {
      String offerId = offer.path("offerId").asText("");
      if (targetOfferId.equals(offerId)) {
        JsonNode priceNode = offer.path("price").path("value");
        if (!priceNode.isMissingNode()) {
          BigDecimal price = new BigDecimal(priceNode.asText("0"));
          return new PriceReadResult(price, responseBody);
        }
      }
    }

    log.warn("Yandex price read: offerId={} not found in response, "
        + "connectionId={}", targetOfferId, connectionId);
    return new PriceReadResult(null, responseBody);
  }

  private long extractBusinessId(Map<String, String> credentials) {
    String value = credentials.get(CredentialKeys.YANDEX_BUSINESS_ID);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Yandex businessId not found in credentials — "
              + "connection metadata may be missing");
    }
    return Long.parseLong(value);
  }

  private JsonNode parseJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse Yandex price read response", e);
    }
  }
}
