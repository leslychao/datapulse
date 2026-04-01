package io.datapulse.execution.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionService;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("StuckStateDetector")
class StuckStateDetectorTest {

  @Mock private PriceActionRepository actionRepository;
  @Mock private ActionService actionService;
  @Mock private ExecutionProperties properties;

  @InjectMocks
  private StuckStateDetector detector;

  private ExecutionProperties.StuckState stuckState;

  @BeforeEach
  void setUp() {
    stuckState = new ExecutionProperties.StuckState(
        Duration.ofMinutes(5),
        Duration.ofMinutes(5),
        Duration.ofMinutes(5),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5));
    lenient().when(properties.getStuckState()).thenReturn(stuckState);
    lenient().when(actionRepository.findStuckInStatus(any(), anyInt()))
        .thenReturn(List.of());
  }

  @Nested
  @DisplayName("escalateStuckExecuting")
  class StuckExecuting {

    @Test
    @DisplayName("should escalate stuck EXECUTING to RECONCILIATION_PENDING")
    void should_escalate_when_executingStuck() {
      var action = stuckAction(ActionStatus.EXECUTING);
      when(actionRepository.findStuckInStatus("EXECUTING", 5))
          .thenReturn(List.of(action));

      detector.detectStuckActions();

      verify(actionService).casReconciliationPending(1L);
    }

    @Test
    @DisplayName("should handle escalation exception gracefully")
    void should_continueProcessing_when_escalationFails() {
      var action = stuckAction(ActionStatus.EXECUTING);
      when(actionRepository.findStuckInStatus("EXECUTING", 5))
          .thenReturn(List.of(action));
      doThrow(new RuntimeException("CAS conflict"))
          .when(actionService).casReconciliationPending(1L);

      detector.detectStuckActions();

      verify(actionService).casReconciliationPending(1L);
    }
  }

  @Nested
  @DisplayName("escalateStuckRetryScheduled")
  class StuckRetryScheduled {

    @Test
    @DisplayName("should fail RETRY_SCHEDULED without next_attempt_at")
    void should_fail_when_retryScheduledWithoutNextAttempt() {
      var action = stuckAction(ActionStatus.RETRY_SCHEDULED);
      action.setNextAttemptAt(null);
      when(actionRepository.findStuckInStatus("RETRY_SCHEDULED", 5))
          .thenReturn(List.of(action));

      detector.detectStuckActions();

      verify(actionService).casFail(eq(1L), eq(ActionStatus.RETRY_SCHEDULED),
          eq(0), eq(ErrorClassification.NON_RETRIABLE), any());
    }

    @Test
    @DisplayName("should skip RETRY_SCHEDULED with next_attempt_at set")
    void should_skip_when_retryScheduledWithNextAttempt() {
      var action = stuckAction(ActionStatus.RETRY_SCHEDULED);
      action.setNextAttemptAt(OffsetDateTime.now().plusMinutes(10));
      when(actionRepository.findStuckInStatus("RETRY_SCHEDULED", 5))
          .thenReturn(List.of(action));

      detector.detectStuckActions();

      verify(actionService, never()).casFail(eq(1L), eq(ActionStatus.RETRY_SCHEDULED),
          anyInt(), any(), any());
    }
  }

  @Nested
  @DisplayName("escalateStuckReconciliation")
  class StuckReconciliation {

    @Test
    @DisplayName("should fail RECONCILIATION_PENDING past timeout")
    void should_fail_when_reconciliationPendingTimeout() {
      var action = stuckAction(ActionStatus.RECONCILIATION_PENDING);
      when(actionRepository.findStuckInStatus("RECONCILIATION_PENDING", 10))
          .thenReturn(List.of(action));

      detector.detectStuckActions();

      verify(actionService).casFail(eq(1L),
          eq(ActionStatus.RECONCILIATION_PENDING),
          eq(0), eq(ErrorClassification.PROVIDER_ERROR), any());
    }
  }

  @Nested
  @DisplayName("escalateStuckScheduled")
  class StuckScheduled {

    @Test
    @DisplayName("should fail SCHEDULED past TTL (outbox delivery failure)")
    void should_fail_when_scheduledPastTtl() {
      var action = stuckAction(ActionStatus.SCHEDULED);
      when(actionRepository.findStuckInStatus("SCHEDULED", 5))
          .thenReturn(List.of(action));

      detector.detectStuckActions();

      verify(actionService).casFail(eq(1L), eq(ActionStatus.SCHEDULED),
          eq(0), eq(ErrorClassification.NON_RETRIABLE), any());
    }
  }

  @Nested
  @DisplayName("no stuck actions")
  class NoStuckActions {

    @Test
    @DisplayName("should not escalate when no stuck actions found")
    void should_doNothing_when_noStuckActions() {
      when(actionRepository.findStuckInStatus(any(), anyInt()))
          .thenReturn(List.of());

      detector.detectStuckActions();

      verify(actionService, never()).casReconciliationPending(anyLong());
      verify(actionService, never()).casFail(anyLong(), any(), anyInt(), any(), any());
    }
  }

  private PriceActionEntity stuckAction(ActionStatus status) {
    var entity = new PriceActionEntity();
    entity.setId(1L);
    entity.setWorkspaceId(10L);
    entity.setMarketplaceOfferId(100L);
    entity.setStatus(status);
    entity.setExecutionMode(ActionExecutionMode.LIVE);
    entity.setTargetPrice(BigDecimal.valueOf(999));
    entity.setCurrentPriceAtCreation(BigDecimal.valueOf(800));
    entity.setMaxAttempts(3);
    entity.setAttemptCount(0);
    entity.setApprovalTimeoutHours(24);
    return entity;
  }
}
