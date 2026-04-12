package io.datapulse.bidding.adapter.ozon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.datapulse.bidding.domain.BidActionGatewayResult;
import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OzonBidCommandAdapterTest {

  @Mock private IntegrationProperties properties;
  @Mock private IntegrationProperties.Ozon ozonProps;
  @Mock private MarketplaceRateLimiter rateLimiter;
  @Mock private OzonPerformanceAuthService authService;

  private OzonBidCommandAdapter adapter;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    lenient().when(properties.getOzon()).thenReturn(ozonProps);
    lenient().when(ozonProps.getPerformanceBaseUrl())
        .thenReturn("https://api-performance.ozon.ru");
    lenient().when(rateLimiter.acquire(any(Long.class), any(RateLimitGroup.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    lenient().when(authService.getAccessToken("client-id-1", "secret-1"))
        .thenReturn("mock-access-token");

    WebClient.Builder builder = mock(WebClient.Builder.class);
    WebClient webClient = mock(WebClient.class);
    WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    lenient().when(builder.build()).thenReturn(webClient);
    lenient().when(webClient.post()).thenReturn(requestSpec);
    lenient().when(requestSpec.uri(any(java.net.URI.class))).thenReturn(bodySpec);
    lenient().when(bodySpec.header(any(), any())).thenReturn(bodySpec);
    lenient().when(bodySpec.contentType(any())).thenReturn(bodySpec);
    lenient().when(bodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
    lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
    lenient().when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{}"));

    adapter = new OzonBidCommandAdapter(builder, properties, rateLimiter, authService);
  }

  @Test
  @DisplayName("marketplaceType is OZON")
  void should_returnOzonMarketplaceType() {
    assertThat(adapter.marketplaceType()).isEqualTo("OZON");
  }

  @Nested
  @DisplayName("Success scenarios")
  class Success {

    @Test
    @DisplayName("returns success when API responds 200")
    void should_succeed_when_apiReturns200() {
      BidActionEntity action = createAction("12345", 1500);

      BidActionGatewayResult result = adapter.execute(
          action, Map.of("client-id", "client-id-1", "client-secret", "secret-1"));

      assertThat(result.success()).isTrue();
      assertThat(result.appliedBid()).isEqualTo(1500);
    }
  }

  @Nested
  @DisplayName("Kopecks to rubles conversion")
  class Conversion {

    @Test
    @DisplayName("converts 1500 kopecks to 15.00 rubles")
    void should_convertKopecksToRubles() {
      assertThat(OzonBidCommandAdapter.kopecksToRubles(1500)).isEqualTo("15.00");
    }

    @Test
    @DisplayName("converts 50 kopecks to 0.50 rubles")
    void should_convertSmallValue() {
      assertThat(OzonBidCommandAdapter.kopecksToRubles(50)).isEqualTo("0.50");
    }

    @Test
    @DisplayName("converts 999 kopecks to 9.99 rubles")
    void should_convertOddValue() {
      assertThat(OzonBidCommandAdapter.kopecksToRubles(999)).isEqualTo("9.99");
    }
  }

  @Nested
  @DisplayName("Failure scenarios")
  class Failure {

    @Test
    @DisplayName("returns failure when credentials are missing")
    void should_fail_when_credentialsMissing() {
      BidActionEntity action = createAction("12345", 1500);

      BidActionGatewayResult result = adapter.execute(action, Map.of());

      assertThat(result.success()).isFalse();
      assertThat(result.errorCode()).isEqualTo("MISSING_CREDENTIALS");
    }

    @Test
    @DisplayName("evicts token on 401 response")
    @SuppressWarnings("unchecked")
    void should_evictToken_when_apiReturns401() {
      WebClient.Builder builder401 = mock(WebClient.Builder.class);
      WebClient webClient = mock(WebClient.class);
      WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class);
      WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
      WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
      WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

      when(builder401.build()).thenReturn(webClient);
      when(webClient.post()).thenReturn(requestSpec);
      when(requestSpec.uri(any(java.net.URI.class))).thenReturn(bodySpec);
      when(bodySpec.header(any(), any())).thenReturn(bodySpec);
      when(bodySpec.contentType(any())).thenReturn(bodySpec);
      when(bodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
      when(headersSpec.retrieve()).thenReturn(responseSpec);
      when(responseSpec.bodyToMono(String.class)).thenReturn(
          Mono.error(WebClientResponseException.create(
              401, "Unauthorized", null, "token expired".getBytes(), null)));

      var adapter401 = new OzonBidCommandAdapter(
          builder401, properties, rateLimiter, authService);
      BidActionEntity action = createAction("12345", 1500);

      BidActionGatewayResult result = adapter401.execute(
          action, Map.of("client-id", "client-id-1", "client-secret", "secret-1"));

      assertThat(result.success()).isFalse();
      assertThat(result.errorCode()).isEqualTo("HTTP_401");
      verify(authService).evict("client-id-1");
    }
  }

  private BidActionEntity createAction(String nmId, int targetBid) {
    var action = new BidActionEntity();
    action.setId(1L);
    action.setCampaignExternalId("campaign-1");
    action.setNmId(nmId);
    action.setTargetBid(targetBid);
    action.setConnectionId(5L);
    return action;
  }
}
