package io.datapulse.etl.adapter.yandex;

import io.datapulse.etl.adapter.util.HttpRetryClassifier;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import io.datapulse.integration.logging.MarketplaceHttpRequestLogger;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class YandexApiCaller {

  private static final String MARKETPLACE = "YANDEX";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;
  private final MarketplaceHttpRequestLogger httpRequestLogger;

  public Flux<DataBuffer> get(String path, long connectionId,
      RateLimitGroup group, String apiKey) {
    return Flux.defer(() -> {
      rateLimiter.acquire(connectionId, group).join();
      URI uri = URI.create(properties.getYandex().getBaseUrl() + path);
      httpRequestLogger.logRequest(
          MARKETPLACE, HttpMethod.GET, uri, connectionId, group, null);
      return webClientBuilder.build()
          .get()
          .uri(uri)
          .header("Api-Key", apiKey)
          .exchangeToFlux(response -> handleResponse(response, connectionId, group));
    }).retryWhen(HttpRetryClassifier.retrySpec());
  }

  public Flux<DataBuffer> post(String path, long connectionId,
      RateLimitGroup group, String apiKey, Object body) {
    return Flux.defer(() -> {
      rateLimiter.acquire(connectionId, group).join();
      URI uri = URI.create(properties.getYandex().getBaseUrl() + path);
      httpRequestLogger.logRequest(
          MARKETPLACE, HttpMethod.POST, uri, connectionId, group, body);
      return webClientBuilder.build()
          .post()
          .uri(uri)
          .header("Api-Key", apiKey)
          .bodyValue(body)
          .exchangeToFlux(response -> handleResponse(response, connectionId, group));
    }).retryWhen(HttpRetryClassifier.retrySpec());
  }

  public Flux<DataBuffer> postNoBody(String path, long connectionId,
      RateLimitGroup group, String apiKey) {
    return Flux.defer(() -> {
      rateLimiter.acquire(connectionId, group).join();
      URI uri = URI.create(properties.getYandex().getBaseUrl() + path);
      httpRequestLogger.logRequest(
          MARKETPLACE, HttpMethod.POST, uri, connectionId, group, null);
      return webClientBuilder.build()
          .post()
          .uri(uri)
          .header("Api-Key", apiKey)
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
