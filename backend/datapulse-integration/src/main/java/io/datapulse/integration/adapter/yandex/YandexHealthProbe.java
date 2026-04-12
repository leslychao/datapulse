package io.datapulse.integration.adapter.yandex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.HealthProbeResult;
import io.datapulse.integration.domain.MarketplaceHealthProbe;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class YandexHealthProbe implements MarketplaceHealthProbe {

    private final IntegrationProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public MarketplaceType marketplaceType() {
        return MarketplaceType.YANDEX;
    }

    @Override
    public HealthProbeResult probe(Map<String, String> credentials) {
        String apiKey = credentials.get(CredentialKeys.YANDEX_API_KEY);
        String baseUrl = properties.getYandex().getBaseUrl();
        try {
            String responseBody = webClientBuilder.baseUrl(baseUrl).build()
                .get()
                .uri("/v2/campaigns")
                .header("Api-Key", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseCampaignsResponse(responseBody);
        } catch (WebClientResponseException.Unauthorized
                 | WebClientResponseException.Forbidden e) {
            return HealthProbeResult.failure(
                MessageCodes.INTEGRATION_YANDEX_TOKEN_INVALID);
        } catch (WebClientResponseException e) {
            return HealthProbeResult.failure("HTTP_" + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Yandex health probe connection error", e);
            return HealthProbeResult.failure("CONNECTION_ERROR");
        }
    }

    private HealthProbeResult parseCampaignsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode campaignsNode = root.path("campaigns");

            if (!campaignsNode.isArray() || campaignsNode.isEmpty()) {
                return HealthProbeResult.failure(
                    MessageCodes.INTEGRATION_YANDEX_BUSINESS_DISABLED);
            }

            Long businessId = null;
            List<Map<String, Object>> campaigns = new ArrayList<>();

            for (JsonNode campaign : campaignsNode) {
                if (businessId == null && campaign.has("business")) {
                    businessId = campaign.path("business").path("id").asLong();
                }
                long campaignId = campaign.path("id").asLong();
                String placementType = campaign.path("placementType").asText("UNKNOWN");
                campaigns.add(Map.of(
                    "campaignId", campaignId,
                    "placementType", placementType));
            }

            Map<String, Object> metadata = new HashMap<>();
            if (businessId != null) {
                metadata.put("businessId", businessId);
            }
            metadata.put("campaigns", campaigns);

            String externalAccountId = businessId != null
                ? String.valueOf(businessId) : null;

            log.info("Yandex health probe success: businessId={}, campaigns={}",
                businessId, campaigns.size());

            return HealthProbeResult.success(externalAccountId, metadata);
        } catch (Exception e) {
            log.error("Failed to parse Yandex campaigns response", e);
            return HealthProbeResult.failure("PARSE_ERROR");
        }
    }
}
