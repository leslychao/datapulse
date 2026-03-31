package io.datapulse.integration.domain;

import io.datapulse.integration.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OzonHealthProbe implements MarketplaceHealthProbe {

    private final IntegrationProperties properties;
    private final WebClient.Builder webClientBuilder;

    @Override
    public MarketplaceType marketplaceType() {
        return MarketplaceType.OZON;
    }

    @Override
    public HealthProbeResult probe(Map<String, String> credentials) {
        String clientId = credentials.get("clientId");
        String apiKey = credentials.get("apiKey");
        String baseUrl = properties.getOzon().getSellerBaseUrl();
        try {
            webClientBuilder.baseUrl(baseUrl).build()
                    .post()
                    .uri("/v3/product/list")
                    .header("Client-Id", clientId)
                    .header("Api-Key", apiKey)
                    .bodyValue(Map.of("filter", Map.of(), "limit", 1))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return HealthProbeResult.success(clientId);
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
            return HealthProbeResult.failure("AUTH_FAILED");
        } catch (WebClientResponseException e) {
            return HealthProbeResult.failure("HTTP_" + e.getStatusCode().value());
        } catch (Exception e) {
            return HealthProbeResult.failure("CONNECTION_ERROR");
        }
    }
}
