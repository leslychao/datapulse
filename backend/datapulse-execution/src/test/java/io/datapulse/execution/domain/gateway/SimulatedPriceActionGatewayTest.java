package io.datapulse.execution.domain.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.AttemptOutcome;
import io.datapulse.execution.domain.OfferExecutionContext;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.SimulatedOfferStateEntity;
import io.datapulse.execution.persistence.SimulatedOfferStateRepository;
import io.datapulse.integration.domain.MarketplaceType;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulatedPriceActionGateway")
class SimulatedPriceActionGatewayTest {

  @Mock private SimulatedOfferStateRepository simulatedStateRepository;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks
  private SimulatedPriceActionGateway gateway;

  @Nested
  @DisplayName("executionMode")
  class ExecutionMode {

    @Test
    @DisplayName("should return SIMULATED")
    void should_returnSimulated() {
      assertThat(gateway.executionMode()).isEqualTo(ActionExecutionMode.SIMULATED);
    }
  }

  @Nested
  @DisplayName("execute — new shadow state")
  class NewShadowState {

    @Test
    @DisplayName("should create new simulated state and return confirmed")
    void should_createStateAndReturnConfirmed_when_noExistingState() {
      var action = simulatedAction(BigDecimal.valueOf(999), BigDecimal.valueOf(800));
      var context = simulatedContext();

      when(simulatedStateRepository.findByWorkspaceIdAndMarketplaceOfferId(10L, 100L))
          .thenReturn(Optional.empty());

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.outcome()).isEqualTo(AttemptOutcome.SUCCESS);

      var captor = ArgumentCaptor.forClass(SimulatedOfferStateEntity.class);
      verify(simulatedStateRepository).save(captor.capture());

      SimulatedOfferStateEntity saved = captor.getValue();
      assertThat(saved.getSimulatedPrice()).isEqualByComparingTo(BigDecimal.valueOf(999));
      assertThat(saved.getCanonicalPriceAtSimulation())
          .isEqualByComparingTo(BigDecimal.valueOf(800));
      assertThat(saved.getPriceDelta()).isEqualByComparingTo(BigDecimal.valueOf(199));
      assertThat(saved.getPreviousSimulatedPrice()).isNull();
      assertThat(saved.getWorkspaceId()).isEqualTo(10L);
      assertThat(saved.getMarketplaceOfferId()).isEqualTo(100L);
    }
  }

  @Nested
  @DisplayName("execute — existing shadow state")
  class ExistingShadowState {

    @Test
    @DisplayName("should update existing state and track previous price")
    void should_updateStateAndTrackPrevious_when_existingState() {
      var action = simulatedAction(BigDecimal.valueOf(1100), BigDecimal.valueOf(800));
      var context = simulatedContext();

      var existing = new SimulatedOfferStateEntity();
      existing.setId(42L);
      existing.setSimulatedPrice(BigDecimal.valueOf(999));
      existing.setWorkspaceId(10L);
      existing.setMarketplaceOfferId(100L);

      when(simulatedStateRepository.findByWorkspaceIdAndMarketplaceOfferId(10L, 100L))
          .thenReturn(Optional.of(existing));

      GatewayResult result = gateway.execute(action, context);

      assertThat(result.isSuccess()).isTrue();

      var captor = ArgumentCaptor.forClass(SimulatedOfferStateEntity.class);
      verify(simulatedStateRepository).save(captor.capture());

      SimulatedOfferStateEntity saved = captor.getValue();
      assertThat(saved.getSimulatedPrice()).isEqualByComparingTo(BigDecimal.valueOf(1100));
      assertThat(saved.getPreviousSimulatedPrice())
          .isEqualByComparingTo(BigDecimal.valueOf(999));
    }
  }

  @Nested
  @DisplayName("execute — delta calculation")
  class DeltaCalculation {

    @Test
    @DisplayName("should calculate positive delta when target > canonical")
    void should_calculatePositiveDelta_when_targetAboveCanonical() {
      var action = simulatedAction(BigDecimal.valueOf(1000), BigDecimal.valueOf(800));
      var context = simulatedContext();
      when(simulatedStateRepository.findByWorkspaceIdAndMarketplaceOfferId(10L, 100L))
          .thenReturn(Optional.empty());

      gateway.execute(action, context);

      var captor = ArgumentCaptor.forClass(SimulatedOfferStateEntity.class);
      verify(simulatedStateRepository).save(captor.capture());

      assertThat(captor.getValue().getPriceDelta())
          .isEqualByComparingTo(BigDecimal.valueOf(200));
      assertThat(captor.getValue().getPriceDeltaPct())
          .isEqualByComparingTo(BigDecimal.valueOf(25));
    }

    @Test
    @DisplayName("should calculate negative delta when target < canonical")
    void should_calculateNegativeDelta_when_targetBelowCanonical() {
      var action = simulatedAction(BigDecimal.valueOf(700), BigDecimal.valueOf(1000));
      var context = simulatedContext();
      when(simulatedStateRepository.findByWorkspaceIdAndMarketplaceOfferId(10L, 100L))
          .thenReturn(Optional.empty());

      gateway.execute(action, context);

      var captor = ArgumentCaptor.forClass(SimulatedOfferStateEntity.class);
      verify(simulatedStateRepository).save(captor.capture());

      assertThat(captor.getValue().getPriceDelta())
          .isEqualByComparingTo(BigDecimal.valueOf(-300));
    }

    @Test
    @DisplayName("should handle zero canonical price without division error")
    void should_handleZeroCanonical_when_canonicalIsZero() {
      var action = simulatedAction(BigDecimal.valueOf(500), BigDecimal.ZERO);
      var context = simulatedContext();
      when(simulatedStateRepository.findByWorkspaceIdAndMarketplaceOfferId(10L, 100L))
          .thenReturn(Optional.empty());

      gateway.execute(action, context);

      var captor = ArgumentCaptor.forClass(SimulatedOfferStateEntity.class);
      verify(simulatedStateRepository).save(captor.capture());

      assertThat(captor.getValue().getPriceDelta())
          .isEqualByComparingTo(BigDecimal.valueOf(500));
      assertThat(captor.getValue().getPriceDeltaPct()).isNull();
    }

    @Test
    @DisplayName("should reset stale deltaPct when canonical becomes zero on update")
    void should_resetDeltaPct_when_canonicalBecomesZeroOnExisting() {
      var action = simulatedAction(BigDecimal.valueOf(500), BigDecimal.ZERO);
      var context = simulatedContext();

      var existing = new SimulatedOfferStateEntity();
      existing.setId(42L);
      existing.setSimulatedPrice(BigDecimal.valueOf(900));
      existing.setPriceDeltaPct(BigDecimal.valueOf(12.5));
      existing.setWorkspaceId(10L);
      existing.setMarketplaceOfferId(100L);

      when(simulatedStateRepository.findByWorkspaceIdAndMarketplaceOfferId(10L, 100L))
          .thenReturn(Optional.of(existing));

      gateway.execute(action, context);

      var captor = ArgumentCaptor.forClass(SimulatedOfferStateEntity.class);
      verify(simulatedStateRepository).save(captor.capture());

      assertThat(captor.getValue().getPriceDelta())
          .isEqualByComparingTo(BigDecimal.valueOf(500));
      assertThat(captor.getValue().getPriceDeltaPct()).isNull();
    }
  }

  private PriceActionEntity simulatedAction(BigDecimal targetPrice, BigDecimal canonicalPrice) {
    var entity = new PriceActionEntity();
    entity.setId(1L);
    entity.setWorkspaceId(10L);
    entity.setMarketplaceOfferId(100L);
    entity.setTargetPrice(targetPrice);
    entity.setCurrentPriceAtCreation(canonicalPrice);
    entity.setStatus(ActionStatus.EXECUTING);
    entity.setExecutionMode(ActionExecutionMode.SIMULATED);
    entity.setMaxAttempts(3);
    entity.setAttemptCount(0);
    entity.setApprovalTimeoutHours(24);
    return entity;
  }

  private OfferExecutionContext simulatedContext() {
    return new OfferExecutionContext(
        100L, 5L, 10L, MarketplaceType.WB,
        "SKU-123", null, Map.of(), null);
  }
}
