package io.datapulse.execution.adapter.ozon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.execution.domain.PriceWriteAdapter;
import io.datapulse.execution.domain.PriceWriteResult;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Ozon price write: synchronous POST → check result[].updated.
 *
 * Ozon returns HTTP 200 with per-item result:
 * - updated: true → CONFIRMED
 * - updated: false + errors[] → classify by error code
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OzonPriceWriteAdapter implements PriceWriteAdapter {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationProperties integrationProperties;
    private final MarketplaceRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public PriceWriteResult setPrice(long connectionId, String marketplaceSku,
                                     BigDecimal targetPrice, Map<String, String> credentials) {
        String baseUrl = integrationProperties.getOzon().getSellerBaseUrl();
        String clientId = credentials.get("client_id");
        String apiKey = credentials.get("api_key");

        var priceItem = Map.of(
                "offer_id", marketplaceSku,
                "price", targetPrice.toPlainString()
        );

        var requestBody = Map.of("prices", List.of(priceItem));

        String requestSummary = serialize(Map.of(
                "endpoint", baseUrl + "/v1/product/import/prices",
                "offer_id", marketplaceSku,
                "targetPrice", targetPrice
        ));

        rateLimiter.acquire(connectionId, RateLimitGroup.OZON_PRICE_UPDATE).join();

        String responseBody = webClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/product/import/prices")
                .header("Client-Id", clientId)
                .header("Api-Key", apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        rateLimiter.onResponse(connectionId, RateLimitGroup.OZON_PRICE_UPDATE, 200);

        return parseOzonResponse(requestSummary, responseBody);
    }

    private PriceWriteResult parseOzonResponse(String requestSummary, String responseBody) {
        JsonNode response = parseJson(responseBody);
        JsonNode results = response.path("result");

        if (!results.isArray() || results.isEmpty()) {
            return PriceWriteResult.rejected(requestSummary, responseBody,
                    "OZON_EMPTY_RESULT", "Empty result array from Ozon");
        }

        JsonNode first = results.get(0);
        boolean updated = first.path("updated").asBoolean(false);

        if (updated) {
            return PriceWriteResult.confirmed(requestSummary, responseBody);
        }

        JsonNode errors = first.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            String errorCode = errors.get(0).path("code").asText("UNKNOWN");
            String errorMessage = errors.get(0).path("message").asText("Unknown error");
            return PriceWriteResult.rejected(requestSummary, responseBody, errorCode, errorMessage);
        }

        return PriceWriteResult.rejected(requestSummary, responseBody,
                "OZON_NOT_UPDATED", "Item not updated, no error details");
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Ozon response", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
