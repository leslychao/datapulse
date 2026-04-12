package io.datapulse.execution.domain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.gateway.GatewayResult;
import io.datapulse.execution.domain.gateway.PriceActionGateway;
import io.datapulse.execution.persistence.PriceActionAttemptRepository;
import io.datapulse.execution.persistence.PriceActionCasRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.platform.observability.MetricsFacade;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceActionExecutor")
class PriceActionExecutorTest {

  @Mock private PriceActionRepository actionRepository;
  @Mock private PriceActionAttemptRepository attemptRepository;
  @Mock private PriceActionCasRepository casRepository;
  @Mock private ActionService actionService;
  @Mock private ExecutionCredentialResolver credentialResolver;
  @Mock private ExecutionProperties properties;
  @Mock private MetricsFacade metrics;
  @Mock private PriceActionGateway liveGateway;
  @Mock private PriceActionGateway simulatedGateway;

  private PriceActionExecutor executor;

  private static final long ACTION_ID = 1L;

  @BeforeEach
  void setUp() {
    when(liveGateway.executionMode()).thenReturn(ActionExecutionMode.LIVE);
    when(simulatedGateway.executionMode()).thenReturn(ActionExecutionMode.SIMULATED);
    executor = new PriceActionExecutor(
        actionRepository, attemptRepository, casRepository,
        actionService, credentialResolver, properties, metrics,
        List.of(liveGateway, simulatedGateway));
  }

  @Nested
  @DisplayName("execute — happy path")
  class ExecuteHappyPath {

    @Test
    @DisplayName("should claim, execute, and schedule reconciliation when LIVE gateway confirms")
    void should_reconcile_when_liveGatewayConfirms() {
      var scheduled = actionEntity(ActionStatus.SCHEDULED);
      var executing = actionEntity(ActionStatus.EXECUTING);
      var context = testContext();

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L)).thenReturn(context);
      when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(liveGateway.execute(any(), any()))
          .thenReturn(GatewayResult.confirmed("{req}", "{resp}"));

      executor.execute(ACTION_ID);

      verify(actionService).casClaim(ACTION_ID);
      verify(casRepository).casIncrementAttempt(ACTION_ID);
      verify(actionService).casReconciliationPending(ACTION_ID);
      verify(actionService, never()).casSucceed(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("should succeed immediately when SIMULATED gateway confirms")
    void should_succeed_when_simulatedGatewayConfirms() {
      var scheduled = simulatedActionEntity(ActionStatus.SCHEDULED);
      var executing = simulatedActionEntity(ActionStatus.EXECUTING);
      var context = testContext();

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L)).thenReturn(context);
      when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(simulatedGateway.execute(any(), any()))
          .thenReturn(GatewayResult.confirmed(null, "{sim_resp}"));

      executor.execute(ACTION_ID);

      verify(actionService).casSucceed(ACTION_ID, ActionStatus.EXECUTING,
          ActionReconciliationSource.AUTO, null);
      verify(actionService, never()).casReconciliationPending(anyLong());
    }

    @Test
    @DisplayName("should transition to RECONCILIATION_PENDING when gateway returns uncertain")
    void should_reconcile_when_gatewayUncertain() {
      var scheduled = actionEntity(ActionStatus.SCHEDULED);
      var executing = actionEntity(ActionStatus.EXECUTING);
      var context = testContext();

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L)).thenReturn(context);
      when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(liveGateway.execute(any(), any()))
          .thenReturn(GatewayResult.uncertain("{req}", "{resp}"));

      executor.execute(ACTION_ID);

      verify(actionService).casReconciliationPending(ACTION_ID);
    }

    @Test
    @DisplayName("should schedule retry when gateway returns retriable and attempts remain")
    void should_scheduleRetry_when_retriableAndAttemptsRemain() {
      var scheduled = actionEntity(ActionStatus.SCHEDULED);
      var executing = actionEntity(ActionStatus.EXECUTING);
      var context = testContext();

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L)).thenReturn(context);
      when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(properties.getMaxAttempts()).thenReturn(3);
      when(liveGateway.execute(any(), any()))
          .thenReturn(GatewayResult.retriable(
              ErrorClassification.RETRIABLE_TRANSIENT, "Server error",
              "{req}", "{resp}"));

      executor.execute(ACTION_ID);

      verify(actionService).casScheduleRetry(ACTION_ID, 1);
    }

    @Test
    @DisplayName("should fail when gateway returns retriable but max attempts reached")
    void should_fail_when_retriableButMaxAttemptsReached() {
      var scheduled = actionEntity(ActionStatus.SCHEDULED);
      var executing = actionEntity(ActionStatus.EXECUTING);
      executing.setAttemptCount(2);
      var context = testContext();

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L)).thenReturn(context);
      when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(properties.getMaxAttempts()).thenReturn(3);
      when(liveGateway.execute(any(), any()))
          .thenReturn(GatewayResult.retriable(
              ErrorClassification.RETRIABLE_TRANSIENT, "Server error",
              "{req}", "{resp}"));

      executor.execute(ACTION_ID);

      verify(actionService).casFail(eq(ACTION_ID), eq(ActionStatus.EXECUTING),
          eq(3), eq(ErrorClassification.RETRIABLE_TRANSIENT), eq("Server error"));
    }

    @Test
    @DisplayName("should fail when gateway returns terminal error")
    void should_fail_when_terminalError() {
      var scheduled = actionEntity(ActionStatus.SCHEDULED);
      var executing = actionEntity(ActionStatus.EXECUTING);
      var context = testContext();

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L)).thenReturn(context);
      when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(liveGateway.execute(any(), any()))
          .thenReturn(GatewayResult.terminal(
              ErrorClassification.NON_RETRIABLE, "Bad request",
              "{req}", "{resp}"));

      executor.execute(ACTION_ID);

      verify(actionService).casFail(eq(ACTION_ID), eq(ActionStatus.EXECUTING),
          eq(1), eq(ErrorClassification.NON_RETRIABLE), eq("Bad request"));
    }
  }

  @Nested
  @DisplayName("execute — edge cases")
  class ExecuteEdgeCases {

    @Test
    @DisplayName("should skip when action not found")
    void should_skip_when_actionNotFound() {
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.empty());

      executor.execute(ACTION_ID);

      verify(actionService, never()).casClaim(anyLong());
    }

    @Test
    @DisplayName("should skip when action not in SCHEDULED state")
    void should_skip_when_notScheduled() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      executor.execute(ACTION_ID);

      verify(actionService, never()).casClaim(anyLong());
    }

    @Test
    @DisplayName("should skip when CAS claim fails (already claimed)")
    void should_skip_when_claimFails() {
      var scheduled = actionEntity(ActionStatus.SCHEDULED);
      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(scheduled));

      executor.execute(ACTION_ID);

      verify(credentialResolver, never()).resolve(anyLong());
    }

    @Test
    @DisplayName("should fail terminally when credential resolution fails")
    void should_failTerminally_when_credentialResolutionFails() {
      var scheduled = actionEntity(ActionStatus.SCHEDULED);
      var executing = actionEntity(ActionStatus.EXECUTING);

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(scheduled))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L))
          .thenThrow(new IllegalStateException("Vault unavailable"));

      executor.execute(ACTION_ID);

      verify(actionService).casFail(eq(ACTION_ID), eq(ActionStatus.EXECUTING),
          eq(0), eq(ErrorClassification.NON_RETRIABLE), any());
    }
  }

  @Nested
  @DisplayName("executeRetry")
  class ExecuteRetry {

    @Test
    @DisplayName("should execute retry and schedule reconciliation when LIVE gateway confirms")
    void should_executeRetry_when_retryScheduled() {
      var retryAction = actionEntity(ActionStatus.RETRY_SCHEDULED);
      retryAction.setAttemptCount(1);
      var executing = actionEntity(ActionStatus.EXECUTING);
      executing.setAttemptCount(1);
      var context = testContext();

      when(actionRepository.findById(ACTION_ID))
          .thenReturn(Optional.of(retryAction))
          .thenReturn(Optional.of(executing));
      when(credentialResolver.resolve(100L)).thenReturn(context);
      when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(liveGateway.execute(any(), any()))
          .thenReturn(GatewayResult.confirmed("{req}", "{resp}"));

      executor.executeRetry(ACTION_ID, 2);

      verify(actionService).casExecuteFromRetry(ACTION_ID);
      verify(actionService).casReconciliationPending(ACTION_ID);
    }

    @Test
    @DisplayName("should skip retry when not in RETRY_SCHEDULED state")
    void should_skipRetry_when_notRetryScheduled() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      executor.executeRetry(ACTION_ID, 2);

      verify(actionService, never()).casExecuteFromRetry(anyLong());
    }
  }

  private PriceActionEntity actionEntity(ActionStatus status) {
    var entity = new PriceActionEntity();
    entity.setId(ACTION_ID);
    entity.setWorkspaceId(10L);
    entity.setMarketplaceOfferId(100L);
    entity.setPriceDecisionId(50L);
    entity.setStatus(status);
    entity.setExecutionMode(ActionExecutionMode.LIVE);
    entity.setTargetPrice(BigDecimal.valueOf(999));
    entity.setCurrentPriceAtCreation(BigDecimal.valueOf(800));
    entity.setMaxAttempts(3);
    entity.setAttemptCount(0);
    entity.setApprovalTimeoutHours(24);
    return entity;
  }

  private PriceActionEntity simulatedActionEntity(ActionStatus status) {
    var entity = actionEntity(status);
    entity.setExecutionMode(ActionExecutionMode.SIMULATED);
    return entity;
  }

  private OfferExecutionContext testContext() {
    return new OfferExecutionContext(
        100L, 5L, 10L, MarketplaceType.WB,
        "SKU-123", null, Map.of("token", "test"), null);
  }
}
