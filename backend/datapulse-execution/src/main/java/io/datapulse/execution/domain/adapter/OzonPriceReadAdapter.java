package io.datapulse.execution.domain.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.integration.config.IntegrationProperties;
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
 * Ozon price read for reconciliation: POST /v5/product/info/prices
 * to verify that the target price was actually applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OzonPriceReadAdapter implements PriceReadAdapter {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationProperties integrationProperties;
    private final MarketplaceRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public PriceReadResult readCurrentPrice(long connectionId, String marketplaceSku,
                                            Map<String, String> credentials) {
        String baseUrl = integrationProperties.getOzon().getSellerBaseUrl();
        String clientId = credentials.get("client_id");
        String apiKey = credentials.get("api_key");

        var requestBody = Map.of(
                "filter", Map.of("offer_id", new String[]{marketplaceSku}),
                "limit", 1
        );

        rateLimiter.acquire(connectionId, RateLimitGroup.OZON_DEFAULT).join();

        String responseBody = webClientBuilder.build()
                .post()
                .uri(baseUrl + "/v5/product/info/prices")
                .header("Client-Id", clientId)
                .header("Api-Key", apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        rateLimiter.onResponse(connectionId, RateLimitGroup.OZON_DEFAULT, 200);

        JsonNode response = parseJson(responseBody);
        JsonNode items = response.path("result").path("items");

        if (items.isArray() && !items.isEmpty()) {
            JsonNode first = items.get(0);
            JsonNode priceNode = first.path("price").path("price");
            if (!priceNode.isMissingNode()) {
                BigDecimal price = new BigDecimal(priceNode.asText("0"));
                return new PriceReadResult(price, responseBody);
            }
        }

        log.warn("Ozon price read: no data for offerId={}, connectionId={}",
                marketplaceSku, connectionId);
        return new PriceReadResult(null, responseBody);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Ozon read response", e);
        }
    }
}
