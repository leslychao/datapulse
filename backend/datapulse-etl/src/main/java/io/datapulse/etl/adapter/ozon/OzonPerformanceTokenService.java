package io.datapulse.etl.adapter.ozon;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.datapulse.etl.adapter.util.HttpRetryClassifier;
import io.datapulse.integration.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Manages OAuth2 client_credentials tokens for Ozon Performance API.
 * Tokens are cached per clientId with a 25 min TTL (token lifetime is 30 min).
 */
@Slf4j
@Service
public class OzonPerformanceTokenService {

  private static final String TOKEN_PATH = "/api/client/token";
  private static final long CACHE_TTL_MINUTES = 25;

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;

  private final Cache<String, String> tokenCache = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
      .maximumSize(100)
      .build();

  public OzonPerformanceTokenService(WebClient.Builder webClientBuilder,
      IntegrationProperties properties) {
    this.webClientBuilder = webClientBuilder;
    this.properties = properties;
  }

  /**
   * Returns a cached or freshly-fetched OAuth2 access token.
   *
   * @param clientId     Ozon Performance client ID
   * @param clientSecret Ozon Performance client secret
   * @return bearer access token
   */
  public String getAccessToken(String clientId, String clientSecret) {
    return tokenCache.get(clientId, key -> fetchToken(key, clientSecret));
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
        .retryWhen(HttpRetryClassifier.retrySpec())
        .doOnError(WebClientResponseException.class, ex -> {
          if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            log.error("Ozon Performance credentials invalid: clientId={}, status={}",
                clientId, ex.getStatusCode().value());
          }
        })
        .block();

    if (response == null || response.accessToken() == null) {
      throw new IllegalStateException(
          "Empty token response from Ozon Performance API: clientId=" + clientId);
    }

    log.info("Ozon Performance OAuth2 token acquired: clientId={}, expiresIn={}s",
        clientId, response.expiresIn());
    return response.accessToken();
  }

  /**
   * Evicts the cached token for the given clientId (e.g., on 401 during API call).
   */
  public void evict(String clientId) {
    tokenCache.invalidate(clientId);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") int expiresIn
  ) {}
}
