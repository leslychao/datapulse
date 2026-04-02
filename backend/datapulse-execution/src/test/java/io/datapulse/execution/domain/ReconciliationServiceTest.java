package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.persistence.PriceActionAttemptEntity;
import io.datapulse.execution.persistence.PriceActionAttemptRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.platform.observability.MetricsFacade;
import io.datapulse.platform.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationService")
class ReconciliationServiceTest {

  @Mock private PriceActionRepository actionRepository;
  @Mock private PriceActionAttemptRepository attemptRepository;
  @Mock private ActionService actionService;
  @Mock private OutboxService outboxService;
  @Mock private ExecutionProperties properties;
  @Mock private MetricsFacade metrics;

  @InjectMocks
  private ReconciliationService service;

  private static final long ACTION_ID = 1L;

  @Nested
  @DisplayName("processReconciliationCheck")
  class ProcessReconciliationCheck {

    @Test
    @DisplayName("should casSucceed when price matches target")
    void should_succeed_when_priceMatches() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));
      when(attemptRepository.findByPriceActionIdAndAttemptNumber(ACTION_ID, 1))
          .thenReturn(Optional.of(new PriceActionAttemptEntity()));

      service.processReconciliationCheck(ACTION_ID, 1,
          BigDecimal.valueOf(999), "{\"snapshot\":true}");

      verify(actionService).casSucceed(ACTION_ID,
          ActionStatus.RECONCILIATION_PENDING,
          ActionReconciliationSource.AUTO, null);
    }

    @Test
    @DisplayName("should schedule next reconciliation when price does not match and attempts remain")
    void should_scheduleNext_when_priceMismatchAndAttemptsRemain() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));
      when(attemptRepository.findByPriceActionIdAndAttemptNumber(ACTION_ID, 1))
          .thenReturn(Optional.of(new PriceActionAttemptEntity()));

      var reconProps = reconProperties(3);
      when(properties.getReconciliation()).thenReturn(reconProps);

      service.processReconciliationCheck(ACTION_ID, 1,
          BigDecimal.valueOf(800), "{\"snapshot\":true}");

      verify(outboxService).createEvent(any(), any(), eq(ACTION_ID), any());
      verify(actionService, never()).casSucceed(anyLong(), any(), any(), any());
      verify(actionService, never()).casFail(anyLong(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("should casFail when price does not match and max attempts reached")
    void should_fail_when_priceMismatchAndMaxAttemptsReached() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));
      when(attemptRepository.findByPriceActionIdAndAttemptNumber(ACTION_ID, 1))
          .thenReturn(Optional.of(new PriceActionAttemptEntity()));

      var reconProps = reconProperties(3);
      when(properties.getReconciliation()).thenReturn(reconProps);

      service.processReconciliationCheck(ACTION_ID, 3,
          BigDecimal.valueOf(800), "{\"snapshot\":true}");

      verify(actionService).casFail(eq(ACTION_ID),
          eq(ActionStatus.RECONCILIATION_PENDING),
          eq(1), eq(ErrorClassification.PROVIDER_ERROR), any());
    }

    @Test
    @DisplayName("should casFail when actual price is null")
    void should_fail_when_actualPriceNull() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));
      when(attemptRepository.findByPriceActionIdAndAttemptNumber(ACTION_ID, 1))
          .thenReturn(Optional.of(new PriceActionAttemptEntity()));

      var reconProps = reconProperties(1);
      when(properties.getReconciliation()).thenReturn(reconProps);

      service.processReconciliationCheck(ACTION_ID, 1, null, null);

      verify(actionService).casFail(eq(ACTION_ID),
          eq(ActionStatus.RECONCILIATION_PENDING),
          anyInt(), eq(ErrorClassification.PROVIDER_ERROR), any());
    }

    @Test
    @DisplayName("should preserve IMMEDIATE source when deferred reconciliation confirms")
    void should_preserveImmediateSource_when_deferredConfirms() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

      var attempt = new PriceActionAttemptEntity();
      attempt.setReconciliationSource(ReconciliationSource.IMMEDIATE);
      attempt.setPriceMatch(true);
      when(attemptRepository.findByPriceActionIdAndAttemptNumber(ACTION_ID, 1))
          .thenReturn(Optional.of(attempt));

      service.processReconciliationCheck(ACTION_ID, 1,
          BigDecimal.valueOf(999), "{\"snapshot\":true}");

      verify(actionService).casSucceed(ACTION_ID,
          ActionStatus.RECONCILIATION_PENDING,
          ActionReconciliationSource.AUTO, null);
      verify(attemptRepository).save(attempt);
      assertThat(attempt.getReconciliationSource()).isEqualTo(ReconciliationSource.IMMEDIATE);
    }

    @Test
    @DisplayName("should skip when action not found")
    void should_skip_when_actionNotFound() {
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.empty());

      service.processReconciliationCheck(ACTION_ID, 1,
          BigDecimal.valueOf(999), null);

      verify(actionService, never()).casSucceed(anyLong(), any(), any(), any());
      verify(actionService, never()).casFail(anyLong(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("should skip when action is not in RECONCILIATION_PENDING")
    void should_skip_when_notReconciliationPending() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      action.setStatus(ActionStatus.SUCCEEDED);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

      service.processReconciliationCheck(ACTION_ID, 1,
          BigDecimal.valueOf(999), null);

      verify(actionService, never()).casSucceed(anyLong(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("processManualReconciliation")
  class ProcessManualReconciliation {

    @Test
    @DisplayName("should casSucceed when manual reconciliation marked as succeeded")
    void should_succeed_when_manualSuccess() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

      service.processManualReconciliation(ACTION_ID, true,
          "Verified manually", 5L);

      verify(actionService).casSucceed(ACTION_ID,
          ActionStatus.RECONCILIATION_PENDING,
          ActionReconciliationSource.MANUAL, "Verified manually");
      verify(attemptRepository).save(any(PriceActionAttemptEntity.class));
    }

    @Test
    @DisplayName("should casFail when manual reconciliation marked as failed")
    void should_fail_when_manualFailure() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

      service.processManualReconciliation(ACTION_ID, false,
          "Price not applied", 5L);

      verify(actionService).casFail(eq(ACTION_ID),
          eq(ActionStatus.RECONCILIATION_PENDING),
          eq(1), eq(ErrorClassification.PROVIDER_ERROR), any());
    }

    @Test
    @DisplayName("should skip when action not in RECONCILIATION_PENDING")
    void should_skip_when_notReconciliationPending() {
      var action = reconciliationAction(BigDecimal.valueOf(999));
      action.setStatus(ActionStatus.FAILED);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

      service.processManualReconciliation(ACTION_ID, true, "reason", 5L);

      verify(actionService, never()).casSucceed(anyLong(), any(), any(), any());
    }
  }

  private PriceActionEntity reconciliationAction(BigDecimal targetPrice) {
    var entity = new PriceActionEntity();
    entity.setId(ACTION_ID);
    entity.setWorkspaceId(10L);
    entity.setMarketplaceOfferId(100L);
    entity.setStatus(ActionStatus.RECONCILIATION_PENDING);
    entity.setTargetPrice(targetPrice);
    entity.setAttemptCount(1);
    entity.setExecutionMode(ActionExecutionMode.LIVE);
    entity.setCurrentPriceAtCreation(BigDecimal.valueOf(800));
    entity.setMaxAttempts(3);
    entity.setApprovalTimeoutHours(24);
    return entity;
  }

  private ExecutionProperties.Reconciliation reconProperties(int maxAttempts) {
    return new ExecutionProperties.Reconciliation(
        Duration.ofSeconds(30), 2, maxAttempts, Duration.ofMinutes(10));
  }
}
