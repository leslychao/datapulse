package io.datapulse.etl.adapter.ozon;

import io.datapulse.etl.adapter.util.HttpRetryClassifier;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class OzonApiCaller {

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;

  public Flux<DataBuffer> get(
      String path,
      long connectionId,
      RateLimitGroup group,
      String clientId,
      String apiKey) {
    return Flux.defer(() -> {
      rateLimiter.acquire(connectionId, group).join();
      String baseUrl = properties.getOzon().getSellerBaseUrl();
      return webClientBuilder.build()
          .get()
          .uri(baseUrl + path)
          .header("Client-Id", clientId)
          .header("Api-Key", apiKey)
          .exchangeToFlux(response -> handleResponse(response, connectionId, group));
    }).retryWhen(HttpRetryClassifier.retrySpec());
  }

  public Flux<DataBuffer> post(String path, Object body,
      long connectionId, RateLimitGroup group,
      String clientId, String apiKey) {
    return Flux.defer(() -> {
      rateLimiter.acquire(connectionId, group).join();
      String baseUrl = properties.getOzon().getSellerBaseUrl();
      return webClientBuilder.build()
          .post()
          .uri(baseUrl + path)
          .header("Client-Id", clientId)
          .header("Api-Key", apiKey)
          .bodyValue(body)
          .exchangeToFlux(response -> handleResponse(response, connectionId, group));
    }).retryWhen(HttpRetryClassifier.retrySpec());
  }

  private Flux<DataBuffer> handleResponse(ClientResponse response,
      long connectionId, RateLimitGroup group) {
    int status = response.statusCode().value();
    rateLimiter.onResponse(connectionId, group, status);
    if (status == 204) {
      return Flux.empty();
    }
    if (response.statusCode().isError()) {
      return response.createException().flatMapMany(Flux::error);
    }
    return response.bodyToFlux(DataBuffer.class);
  }
}
