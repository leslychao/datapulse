package io.datapulse.bidding.adapter.wb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.datapulse.bidding.domain.BidActionGatewayResult;
import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WbBidCommandAdapterTest {

  @Mock private IntegrationProperties properties;
  @Mock private IntegrationProperties.Wildberries wbProps;
  @Mock private MarketplaceRateLimiter rateLimiter;

  private WbBidCommandAdapter adapter;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    lenient().when(properties.getWildberries()).thenReturn(wbProps);
    lenient().when(wbProps.getAdvertBaseUrl())
        .thenReturn("https://advert-api.wildberries.ru");
    lenient().when(rateLimiter.acquire(any(Long.class), any(RateLimitGroup.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    WebClient.Builder builder = mock(WebClient.Builder.class);
    WebClient webClient = mock(WebClient.class);
    WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    lenient().when(builder.build()).thenReturn(webClient);
    lenient().when(webClient.method(any(HttpMethod.class))).thenReturn(requestSpec);
    lenient().when(requestSpec.uri(any(java.net.URI.class))).thenReturn(bodySpec);
    lenient().when(bodySpec.header(any(), any())).thenReturn(bodySpec);
    lenient().when(bodySpec.contentType(any())).thenReturn(bodySpec);
    lenient().when(bodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
    lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
    lenient().when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(""));

    adapter = new WbBidCommandAdapter(builder, properties, rateLimiter);
  }

  @Test
  @DisplayName("returns success when PATCH /adv/v1/bids responds 200")
  void should_succeed_when_apiReturns200() {
    BidActionEntity action = createAction("12345", "67890", 150);

    BidActionGatewayResult result = adapter.execute(
        action, Map.of("token", "test-token"));

    assertThat(result.success()).isTrue();
    assertThat(result.appliedBid()).isEqualTo(150);
  }

  @Test
  @DisplayName("returns failure when token is missing in credentials")
  void should_fail_when_tokenMissing() {
    BidActionEntity action = createAction("12345", "67890", 150);

    BidActionGatewayResult result = adapter.execute(action, Map.of());

    assertThat(result.success()).isFalse();
    assertThat(result.errorCode()).isEqualTo("MISSING_TOKEN");
  }

  @Test
  @DisplayName("returns failure with HTTP_429 error code on rate limit")
  @SuppressWarnings("unchecked")
  void should_fail_when_apiReturns429() {
    WebClient.Builder builder429 = mock(WebClient.Builder.class);
    WebClient webClient = mock(WebClient.class);
    WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(builder429.build()).thenReturn(webClient);
    when(webClient.method(any(HttpMethod.class))).thenReturn(requestSpec);
    when(requestSpec.uri(any(java.net.URI.class))).thenReturn(bodySpec);
    when(bodySpec.header(any(), any())).thenReturn(bodySpec);
    when(bodySpec.contentType(any())).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(String.class)).thenReturn(
        Mono.error(WebClientResponseException.create(
            429, "Too Many Requests", null, "rate limited".getBytes(), null)));

    var adapterRateLimit = new WbBidCommandAdapter(builder429, properties, rateLimiter);
    BidActionEntity action = createAction("12345", "67890", 150);

    BidActionGatewayResult result = adapterRateLimit.execute(
        action, Map.of("token", "test-token"));

    assertThat(result.success()).isFalse();
    assertThat(result.errorCode()).isEqualTo("HTTP_429");
  }

  @Test
  @DisplayName("returns failure with HTTP_400 error code on bad request")
  @SuppressWarnings("unchecked")
  void should_fail_when_apiReturns400() {
    WebClient.Builder builder400 = mock(WebClient.Builder.class);
    WebClient webClient = mock(WebClient.class);
    WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(builder400.build()).thenReturn(webClient);
    when(webClient.method(any(HttpMethod.class))).thenReturn(requestSpec);
    when(requestSpec.uri(any(java.net.URI.class))).thenReturn(bodySpec);
    when(bodySpec.header(any(), any())).thenReturn(bodySpec);
    when(bodySpec.contentType(any())).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(String.class)).thenReturn(
        Mono.error(WebClientResponseException.create(
            400, "Bad Request", null, "invalid params".getBytes(), null)));

    var adapter400 = new WbBidCommandAdapter(builder400, properties, rateLimiter);
    BidActionEntity action = createAction("12345", "67890", 150);

    BidActionGatewayResult result = adapter400.execute(
        action, Map.of("token", "test-token"));

    assertThat(result.success()).isFalse();
    assertThat(result.errorCode()).isEqualTo("HTTP_400");
  }

  @Test
  @DisplayName("marketplaceType is WB")
  void should_returnWbMarketplaceType() {
    assertThat(adapter.marketplaceType()).isEqualTo("WB");
  }

  private BidActionEntity createAction(
      String campaignExternalId, String nmId, int targetBid) {
    var action = new BidActionEntity();
    action.setId(1L);
    action.setCampaignExternalId(campaignExternalId);
    action.setNmId(nmId);
    action.setTargetBid(targetBid);
    action.setConnectionId(5L);
    return action;
  }
}
