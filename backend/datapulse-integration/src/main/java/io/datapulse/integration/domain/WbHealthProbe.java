package io.datapulse.integration.domain;

import io.datapulse.integration.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WbHealthProbe implements MarketplaceHealthProbe {

    private final IntegrationProperties properties;
    private final WebClient.Builder webClientBuilder;

    @Override
    public MarketplaceType marketplaceType() {
        return MarketplaceType.WB;
    }

    @Override
    public HealthProbeResult probe(Map<String, String> credentials) {
        String apiToken = credentials.get("apiToken");
        String baseUrl = properties.getWildberries().getContentBaseUrl();
        try {
            webClientBuilder.baseUrl(baseUrl).build()
                    .post()
                    .uri("/content/v2/get/cards/list")
                    .header("Authorization", apiToken)
                    .bodyValue(Map.of("settings", Map.of("cursor", Map.of("limit", 1))))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return HealthProbeResult.success(null);
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
            return HealthProbeResult.failure("AUTH_FAILED");
        } catch (WebClientResponseException e) {
            return HealthProbeResult.failure("HTTP_" + e.getStatusCode().value());
        } catch (Exception e) {
            return HealthProbeResult.failure("CONNECTION_ERROR");
        }
    }
}
