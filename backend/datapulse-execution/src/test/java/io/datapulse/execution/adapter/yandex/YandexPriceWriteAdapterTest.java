package io.datapulse.execution.adapter.yandex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.execution.domain.PriceWriteResult;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class YandexPriceWriteAdapterTest {

  @Mock IntegrationProperties integrationProperties;
  @Mock MarketplaceRateLimiter rateLimiter;
  @Mock WebClient.Builder webClientBuilder;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private YandexPriceWriteAdapter adapter;

  private static final Map<String, String> CREDENTIALS = Map.of(
      "apiKey", "ACMA:test-key",
      "businessId", "67890"
  );

  @BeforeEach
  void setUp() {
    adapter = new YandexPriceWriteAdapter(
        webClientBuilder, integrationProperties, rateLimiter, objectMapper);

    var yandexProps = mock(IntegrationProperties.Yandex.class);
    lenient().when(integrationProperties.getYandex()).thenReturn(yandexProps);
    lenient().when(yandexProps.getWriteBaseUrl()).thenReturn("http://localhost:9091");

    lenient().when(rateLimiter.acquire(anyLong(), any(RateLimitGroup.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("marketplace() should return YANDEX")
  void should_returnYandexMarketplace() {
    assertThat(adapter.marketplace()).isEqualTo(MarketplaceType.YANDEX);
  }

  @Nested
  @DisplayName("setPrice()")
  class SetPrice {

    @Test
    @DisplayName("should return CONFIRMED when Yandex responds with status OK")
    void should_writePrice_successfully() {
      mockWebClientResponse("{\"status\": \"OK\"}");

      PriceWriteResult result = adapter.setPrice(
          1L, "TEST-SKU-001", BigDecimal.valueOf(5990), CREDENTIALS);

      assertThat(result.outcome())
          .isEqualTo(PriceWriteResult.WriteOutcome.CONFIRMED);
      assertThat(result.providerResponseSummary()).contains("OK");

      verify(rateLimiter).acquire(1L, RateLimitGroup.YANDEX_PRICE_UPDATE);
      verify(rateLimiter).onResponse(1L, RateLimitGroup.YANDEX_PRICE_UPDATE, 200);
    }

    @Test
    @DisplayName("should return REJECTED when Yandex responds with error status")
    void should_returnRejected_when_statusNotOk() {
      mockWebClientResponse("{\"status\": \"ERROR\"}");

      PriceWriteResult result = adapter.setPrice(
          1L, "TEST-SKU-001", BigDecimal.valueOf(5990), CREDENTIALS);

      assertThat(result.outcome())
          .isEqualTo(PriceWriteResult.WriteOutcome.REJECTED);
      assertThat(result.errorCode()).isEqualTo("YANDEX_STATUS_ERROR");
    }

    @Test
    @DisplayName("should return UNCERTAIN when response parsing fails")
    void should_returnUncertain_when_invalidResponseBody() {
      mockWebClientResponse("not-json");

      PriceWriteResult result = adapter.setPrice(
          1L, "TEST-SKU-001", BigDecimal.valueOf(5990), CREDENTIALS);

      assertThat(result.outcome())
          .isEqualTo(PriceWriteResult.WriteOutcome.UNCERTAIN);
    }

    @Test
    @DisplayName("should rethrow WebClientResponseException on 420 rate limit")
    void should_rethrow_when_420RateLimit() {
      mockWebClientError(420);

      assertThatThrownBy(() -> adapter.setPrice(
          1L, "TEST-SKU-001", BigDecimal.valueOf(5990), CREDENTIALS))
          .isInstanceOf(WebClientResponseException.class);

      verify(rateLimiter).onResponse(1L, RateLimitGroup.YANDEX_PRICE_UPDATE, 420);
    }

    @Test
    @DisplayName("should rethrow WebClientResponseException on 400 validation error")
    void should_rethrow_when_400ValidationError() {
      mockWebClientError(400);

      assertThatThrownBy(() -> adapter.setPrice(
          1L, "TEST-SKU-001", BigDecimal.valueOf(5990), CREDENTIALS))
          .isInstanceOf(WebClientResponseException.class);

      verify(rateLimiter).onResponse(1L, RateLimitGroup.YANDEX_PRICE_UPDATE, 400);
    }

    @Test
    @DisplayName("should throw IllegalStateException when businessId missing")
    void should_throwException_when_noBusinessId() {
      Map<String, String> creds = Map.of("apiKey", "ACMA:test-key");

      assertThatThrownBy(() -> adapter.setPrice(
          1L, "TEST-SKU-001", BigDecimal.valueOf(5990), creds))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("businessId");
    }
  }

  @SuppressWarnings("unchecked")
  private void mockWebClientResponse(String body) {
    var webClient = mock(WebClient.class);
    var uriSpec = mock(WebClient.RequestBodyUriSpec.class);
    var bodySpec = mock(WebClient.RequestBodySpec.class);
    var headersSpec = mock(WebClient.RequestHeadersSpec.class);
    var responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClientBuilder.build()).thenReturn(webClient);
    when(webClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri(any(String.class))).thenReturn(bodySpec);
    when(bodySpec.header(any(), any())).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(body));
  }

  @SuppressWarnings("unchecked")
  private void mockWebClientError(int statusCode) {
    var webClient = mock(WebClient.class);
    var uriSpec = mock(WebClient.RequestBodyUriSpec.class);
    var bodySpec = mock(WebClient.RequestBodySpec.class);
    var headersSpec = mock(WebClient.RequestHeadersSpec.class);
    var responseSpec = mock(WebClient.ResponseSpec.class);

    var exception = WebClientResponseException.create(
        statusCode, "Error", null, null, null);

    when(webClientBuilder.build()).thenReturn(webClient);
    when(webClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri(any(String.class))).thenReturn(bodySpec);
    when(bodySpec.header(any(), any())).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(exception));
  }
}
