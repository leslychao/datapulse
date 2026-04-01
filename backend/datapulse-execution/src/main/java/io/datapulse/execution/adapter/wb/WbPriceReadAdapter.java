package io.datapulse.execution.adapter.wb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.execution.domain.PriceReadAdapter;
import io.datapulse.execution.domain.PriceReadResult;
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
 * WB price read for reconciliation: GET /api/v2/list/goods/filter
 * to verify that the target price was actually applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WbPriceReadAdapter implements PriceReadAdapter {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationProperties integrationProperties;
    private final MarketplaceRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public PriceReadResult readCurrentPrice(long connectionId, String marketplaceSku,
                                            Map<String, String> credentials) {
        String baseUrl = integrationProperties.getWildberries().getPricesBaseUrl();
        String token = credentials.get("token");
        long nmId = Long.parseLong(marketplaceSku);

        var requestBody = Map.of(
                "limit", 1,
                "offset", 0,
                "filterNmID", nmId
        );

        rateLimiter.acquire(connectionId, RateLimitGroup.WB_PRICES_READ).join();

        String responseBody = webClientBuilder.build()
                .post()
                .uri(baseUrl + "/api/v2/list/goods/filter")
                .header("Authorization", token)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        rateLimiter.onResponse(connectionId, RateLimitGroup.WB_PRICES_READ, 200);

        JsonNode response = parseJson(responseBody);
        JsonNode goods = response.path("data").path("listGoods");

        if (goods.isArray() && !goods.isEmpty()) {
            JsonNode item = goods.get(0);
            JsonNode sizes = item.path("sizes");
            if (sizes.isArray() && !sizes.isEmpty()) {
                BigDecimal price = new BigDecimal(sizes.get(0).path("price").asText("0"));
                return new PriceReadResult(price, responseBody);
            }
        }

        log.warn("WB price read: no data for nmId={}, connectionId={}", nmId, connectionId);
        return new PriceReadResult(null, responseBody);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse WB read response", e);
        }
    }
}
