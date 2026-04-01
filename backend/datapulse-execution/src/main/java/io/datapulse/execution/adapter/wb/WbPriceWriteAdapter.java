package io.datapulse.execution.adapter.wb;

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
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WB price write: POST upload task → poll upload result.
 *
 * Immediate reconciliation within this adapter:
 * - Poll upload details (3s + 4s, max 2 polls) per execution.md
 * - If errorText empty → CONFIRMED
 * - If poll still empty after 2 attempts → UNCERTAIN (deferred reconciliation needed)
 * - If errorText present → REJECTED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WbPriceWriteAdapter implements PriceWriteAdapter {

    private static final Duration FIRST_POLL_DELAY = Duration.ofSeconds(3);
    private static final Duration SECOND_POLL_DELAY = Duration.ofSeconds(4);
    private static final int MAX_POLLS = 2;

    private final WebClient.Builder webClientBuilder;
    private final IntegrationProperties integrationProperties;
    private final MarketplaceRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public PriceWriteResult setPrice(long connectionId, String marketplaceSku,
                                     BigDecimal targetPrice, Map<String, String> credentials) {
        String baseUrl = integrationProperties.getWildberries().getPricesBaseUrl();
        String token = credentials.get("token");
        long nmId = Long.parseLong(marketplaceSku);

        var requestBody = Map.of("data", List.of(Map.of(
                "nmID", nmId,
                "price", targetPrice.intValue()
        )));

        String requestSummary = serialize(Map.of(
                "endpoint", baseUrl + "/api/v2/upload/task",
                "nmID", nmId,
                "targetPrice", targetPrice
        ));

        rateLimiter.acquire(connectionId, RateLimitGroup.WB_PRICE_UPDATE).join();

        String responseBody = webClientBuilder.build()
                .post()
                .uri(baseUrl + "/api/v2/upload/task")
                .header("Authorization", token)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        rateLimiter.onResponse(connectionId, RateLimitGroup.WB_PRICE_UPDATE, 200);

        JsonNode response = parseJson(responseBody);
        if (response.path("error").asBoolean(true)) {
            String errorText = response.path("errorText").asText("Unknown WB error");
            return PriceWriteResult.rejected(requestSummary, responseBody, "WB_UPLOAD_ERROR", errorText);
        }

        long uploadId = response.path("data").path("id").asLong();
        return pollUploadResult(connectionId, token, baseUrl, uploadId, requestSummary);
    }

    private PriceWriteResult pollUploadResult(long connectionId, String token,
                                               String baseUrl, long uploadId,
                                               String requestSummary) {
        Duration[] delays = {FIRST_POLL_DELAY, SECOND_POLL_DELAY};

        for (int i = 0; i < MAX_POLLS; i++) {
            try {
                Thread.sleep(delays[i].toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PriceWriteResult.uncertain(requestSummary,
                        serialize(Map.of("uploadId", uploadId, "pollInterrupted", true)));
            }

            rateLimiter.acquire(connectionId, RateLimitGroup.WB_PRICE_UPDATE).join();

            String pollResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/v2/history/goods/task", b -> b
                            .queryParam("uploadID", uploadId)
                            .build())
                    .header("Authorization", token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            rateLimiter.onResponse(connectionId, RateLimitGroup.WB_PRICE_UPDATE, 200);

            JsonNode pollJson = parseJson(pollResponse);
            JsonNode historyGoods = pollJson.path("data").path("historyGoods");

            if (historyGoods.isArray() && !historyGoods.isEmpty()) {
                JsonNode first = historyGoods.get(0);
                String errorText = first.path("errorText").asText("");

                if (errorText.isEmpty()) {
                    return PriceWriteResult.confirmed(requestSummary, pollResponse);
                } else {
                    return PriceWriteResult.rejected(requestSummary, pollResponse,
                            "WB_ITEM_ERROR", errorText);
                }
            }
        }

        return PriceWriteResult.uncertain(requestSummary,
                serialize(Map.of("uploadId", uploadId, "pollsExhausted", true)));
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse WB response", e);
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
