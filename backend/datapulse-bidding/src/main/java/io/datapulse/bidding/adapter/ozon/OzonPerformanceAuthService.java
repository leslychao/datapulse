package io.datapulse.bidding.adapter.ozon;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.datapulse.integration.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Manages OAuth2 client_credentials tokens for Ozon Performance API.
 * Mirrors the logic from datapulse-etl's OzonPerformanceTokenService,
 * scoped to the bidding module to avoid cross-module dependency.
 */
@Slf4j
@Service
public class OzonPerformanceAuthService {

  private static final String TOKEN_PATH = "/api/client/token";
  private static final long CACHE_TTL_MINUTES = 25;

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;

  private final Cache<String, String> tokenCache = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
      .maximumSize(100)
      .build();

  public OzonPerformanceAuthService(WebClient.Builder webClientBuilder,
      IntegrationProperties properties) {
    this.webClientBuilder = webClientBuilder;
    this.properties = properties;
  }

  public String getAccessToken(String clientId, String clientSecret) {
    return tokenCache.get(clientId, key -> fetchToken(key, clientSecret));
  }

  public void evict(String clientId) {
    tokenCache.invalidate(clientId);
  }

  private String fetchToken(String clientId, String clientSecret) {
    String baseUrl = properties.getOzon().getPerformanceBaseUrl();

    log.debug("Fetching Ozon Performance OAuth2 token: clientId={}", clientId);

    TokenResponse response = webClientBuilder.build()
        .post()
        .uri(baseUrl + TOKEN_PATH)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData("grant_type", "client_credentials")
            .with("client_id", clientId)
            .with("client_secret", clientSecret))
        .retrieve()
        .bodyToMono(TokenResponse.class)
        .block();

    if (response == null || response.accessToken() == null) {
      throw new IllegalStateException(
          "Empty token response from Ozon Performance API: clientId="
              + clientId);
    }

    log.info("Ozon Performance OAuth2 token acquired: clientId={}, "
        + "expiresIn={}s", clientId, response.expiresIn());
    return response.accessToken();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") int expiresIn
  ) {}
}
