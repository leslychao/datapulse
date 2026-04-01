package io.datapulse.execution.domain.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.AttemptOutcome;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.ErrorClassifier;
import io.datapulse.execution.domain.OfferExecutionContext;
import io.datapulse.execution.domain.PriceWriteAdapter;
import io.datapulse.execution.domain.PriceWriteResult;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.integration.domain.MarketplaceType;

@ExtendWith(MockitoExtension.class)
@DisplayName("LivePriceActionGateway")
class LivePriceActionGatewayTest {

  @Mock private PriceWriteAdapter wbAdapter;
  @Mock private PriceWriteAdapter ozonAdapter;

  private LivePriceActionGateway gateway;
  private final ErrorClassifier errorClassifier = new ErrorClassifier();

  @BeforeEach
  void setUp() {
    when(wbAdapter.marketplace()).thenReturn(MarketplaceType.WB);
    when(ozonAdapter.marketplace()).thenReturn(MarketplaceType.OZON);
    gateway = new LivePriceActionGateway(List.of(wbAdapter, ozonAdapter), errorClassifier);
  }

  @Nested
  @DisplayName("executionMode")
  class ExecutionMode {

    @Test
    @DisplayName("should return LIVE")
    void should_returnLive() {
      assertThat(gateway.executionMode()).isEqualTo(ActionExecutionMode.LIVE);
    }
  }

  @Nested
  @DisplayName("execute — write result translation")
  class WriteResultTranslation {

    @Test
    @DisplayName("should return confirmed when adapter confirms")
    void should_returnConfirmed_when_adapterConfirms() {
      var action = actionEntity(MarketplaceType.WB);
      var context = contextFor(MarketplaceType.WB);
      when(wbAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenReturn(PriceWriteResult.confirmed("{req}", "{resp}"));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.outcome()).isEqualTo(AttemptOutcome.SUCCESS);
      assertThat(result.providerRequestSummary()).isEqualTo("{req}");
    }

    @Test
    @DisplayName("should return uncertain when adapter returns uncertain")
    void should_returnUncertain_when_adapterUncertain() {
      var action = actionEntity(MarketplaceType.WB);
      var context = contextFor(MarketplaceType.WB);
      when(wbAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenReturn(PriceWriteResult.uncertain("{req}", "{resp}"));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.isUncertain()).isTrue();
      assertThat(result.outcome()).isEqualTo(AttemptOutcome.UNCERTAIN);
    }

    @Test
    @DisplayName("should return terminal when WB adapter rejects")
    void should_returnTerminal_when_wbAdapterRejects() {
      var action = actionEntity(MarketplaceType.WB);
      var context = contextFor(MarketplaceType.WB);
      when(wbAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenReturn(PriceWriteResult.rejected("{req}", "{resp}",
              "WB_UPLOAD_ERROR", "Invalid price"));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.NON_RETRIABLE_FAILURE);
      assertThat(result.errorClassification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
    }

    @Test
    @DisplayName("should return retriable when Ozon rate-limit error code")
    void should_returnRetriable_when_ozonRateLimitRejection() {
      var action = actionEntity(MarketplaceType.OZON);
      var context = contextFor(MarketplaceType.OZON);
      when(ozonAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenReturn(PriceWriteResult.rejected("{req}", "{resp}",
              "RATE_LIMIT_EXCEEDED", "Too many requests"));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.isRetriable()).isTrue();
      assertThat(result.errorClassification()).isEqualTo(ErrorClassification.RETRIABLE_RATE_LIMIT);
    }

    @Test
    @DisplayName("should return terminal when Ozon non-retriable error code")
    void should_returnTerminal_when_ozonNonRetriableRejection() {
      var action = actionEntity(MarketplaceType.OZON);
      var context = contextFor(MarketplaceType.OZON);
      when(ozonAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenReturn(PriceWriteResult.rejected("{req}", "{resp}",
              "PRODUCT_NOT_FOUND", "Not found"));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.NON_RETRIABLE_FAILURE);
    }
  }

  @Nested
  @DisplayName("execute — adapter exceptions")
  class AdapterExceptions {

    @Test
    @DisplayName("should classify connection exception as retriable")
    void should_returnRetriable_when_connectException() {
      var action = actionEntity(MarketplaceType.WB);
      var context = contextFor(MarketplaceType.WB);
      when(wbAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenThrow(new RuntimeException(new ConnectException("Connection refused")));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.NON_RETRIABLE_FAILURE);
    }

    @Test
    @DisplayName("should classify HTTP 429 exception as retriable")
    void should_returnRetriable_when_http429Exception() {
      var action = actionEntity(MarketplaceType.WB);
      var context = contextFor(MarketplaceType.WB);
      when(wbAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenThrow(WebClientResponseException.create(
              429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.isRetriable()).isTrue();
    }

    @Test
    @DisplayName("should classify HTTP 400 exception as terminal")
    void should_returnTerminal_when_http400Exception() {
      var action = actionEntity(MarketplaceType.WB);
      var context = contextFor(MarketplaceType.WB);
      when(wbAdapter.setPrice(anyLong(), anyString(), any(), any()))
          .thenThrow(WebClientResponseException.create(
              400, "Bad Request", HttpHeaders.EMPTY, new byte[0], null));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.NON_RETRIABLE_FAILURE);
    }
  }

  @Nested
  @DisplayName("execute — no adapter")
  class NoAdapter {

    @Test
    @DisplayName("should return terminal when no adapter for marketplace")
    void should_returnTerminal_when_noAdapterForMarketplace() {
      var action = actionEntity(MarketplaceType.WB);
      var context = new OfferExecutionContext(
          100L, 5L, 10L, MarketplaceType.valueOf("WB"),
          "SKU-123", null, Map.of());

      var gatewayWithNoAdapters = new LivePriceActionGateway(
          List.of(), errorClassifier);

      GatewayResult result = gatewayWithNoAdapters.execute(action, context);

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.NON_RETRIABLE_FAILURE);
      assertThat(result.errorMessage()).contains("No write adapter");
    }
  }

  private PriceActionEntity actionEntity(MarketplaceType marketplace) {
    var entity = new PriceActionEntity();
    entity.setId(1L);
    entity.setWorkspaceId(10L);
    entity.setMarketplaceOfferId(100L);
    entity.setTargetPrice(BigDecimal.valueOf(999));
    entity.setCurrentPriceAtCreation(BigDecimal.valueOf(800));
    entity.setStatus(ActionStatus.EXECUTING);
    entity.setExecutionMode(ActionExecutionMode.LIVE);
    entity.setMaxAttempts(3);
    entity.setAttemptCount(0);
    entity.setApprovalTimeoutHours(24);
    return entity;
  }

  private OfferExecutionContext contextFor(MarketplaceType marketplace) {
    return new OfferExecutionContext(
        100L, 5L, 10L, marketplace,
        "SKU-123", null, Map.of("token", "test-token"));
  }
}
