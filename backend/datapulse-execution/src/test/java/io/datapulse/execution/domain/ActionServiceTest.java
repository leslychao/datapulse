package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.event.ActionCompletedEvent;
import io.datapulse.execution.domain.event.ActionCreatedEvent;
import io.datapulse.execution.domain.event.ActionFailedEvent;
import io.datapulse.execution.persistence.DeferredActionRepository;
import io.datapulse.execution.persistence.PriceActionAttemptRepository;
import io.datapulse.execution.persistence.PriceActionCasRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionQueryRepository;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.execution.persistence.PriceActionStateTransitionRepository;
import io.datapulse.platform.audit.AuditEvent;
import io.datapulse.platform.outbox.OutboxService;
import io.datapulse.platform.security.WorkspaceContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionService")
class ActionServiceTest {

  @Mock private PriceActionRepository actionRepository;
  @Mock private PriceActionCasRepository casRepository;
  @Mock private PriceActionAttemptRepository attemptRepository;
  @Mock private PriceActionQueryRepository queryRepository;
  @Mock private PriceActionStateTransitionRepository stateTransitionRepository;
  @Mock private DeferredActionRepository deferredActionRepository;
  @Mock private OutboxService outboxService;
  @Mock private ExecutionProperties properties;
  @Mock private WorkspaceContext workspaceContext;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private ActionService service;

  private static final long ACTION_ID = 1L;
  private static final long WORKSPACE_ID = 10L;
  private static final long OFFER_ID = 100L;
  private static final long USER_ID = 5L;

  @Nested
  @DisplayName("getAction")
  class GetAction {

    @Test
    @DisplayName("should return action when found")
    void should_returnAction_when_exists() {
      var entity = actionEntity(ActionStatus.PENDING_APPROVAL);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      PriceActionEntity result = service.getAction(ACTION_ID);

      assertThat(result).isEqualTo(entity);
    }

    @Test
    @DisplayName("should throw NotFoundException when action not found")
    void should_throwNotFound_when_actionMissing() {
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getAction(ACTION_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("createAction")
  class CreateAction {

    @Test
    @DisplayName("should create action with PENDING_APPROVAL when autoApprove is false")
    void should_createPendingApproval_when_notAutoApproved() {
      when(actionRepository.findActiveByOfferAndModeForUpdate(
          eq(OFFER_ID), eq(ActionExecutionMode.LIVE.name())))
          .thenReturn(Optional.empty());
      when(properties.getMaxAttempts()).thenReturn(3);
      when(actionRepository.save(any())).thenAnswer(inv -> {
        PriceActionEntity e = inv.getArgument(0);
        e.setId(ACTION_ID);
        return e;
      });

      PriceActionEntity result = service.createAction(
          WORKSPACE_ID, OFFER_ID, 50L,
          ActionExecutionMode.LIVE,
          BigDecimal.valueOf(999), BigDecimal.valueOf(800),
          24, false);

      assertThat(result.getStatus()).isEqualTo(ActionStatus.PENDING_APPROVAL);
      assertThat(result.getApprovedAt()).isNull();
      verify(eventPublisher).publishEvent(any(ActionCreatedEvent.class));
    }

    @Test
    @DisplayName("should create action with APPROVED and schedule when autoApprove is true")
    void should_createApproved_when_autoApproved() {
      when(actionRepository.findActiveByOfferAndModeForUpdate(
          eq(OFFER_ID), eq(ActionExecutionMode.LIVE.name())))
          .thenReturn(Optional.empty());
      when(properties.getMaxAttempts()).thenReturn(3);
      when(actionRepository.save(any())).thenAnswer(inv -> {
        PriceActionEntity e = inv.getArgument(0);
        e.setId(ACTION_ID);
        return e;
      });
      when(casRepository.casTransition(ACTION_ID, ActionStatus.APPROVED, ActionStatus.SCHEDULED))
          .thenReturn(1);

      PriceActionEntity result = service.createAction(
          WORKSPACE_ID, OFFER_ID, 50L,
          ActionExecutionMode.LIVE,
          BigDecimal.valueOf(999), BigDecimal.valueOf(800),
          24, true);

      assertThat(result.getStatus()).isEqualTo(ActionStatus.APPROVED);
      assertThat(result.getApprovedAt()).isNotNull();
      verify(outboxService).createEvent(any(), any(), eq(ACTION_ID), any());
    }

    @Test
    @DisplayName("should supersede existing pre-execution action")
    void should_supersede_when_existingPreExecutionAction() {
      var existing = actionEntity(ActionStatus.APPROVED);
      existing.setId(99L);
      when(actionRepository.findActiveByOfferAndModeForUpdate(
          OFFER_ID, ActionExecutionMode.LIVE.name()))
          .thenReturn(Optional.of(existing));
      when(casRepository.casSupersede(eq(99L), eq(ActionStatus.APPROVED), anyLong()))
          .thenReturn(1);
      when(properties.getMaxAttempts()).thenReturn(3);
      when(actionRepository.save(any())).thenAnswer(inv -> {
        PriceActionEntity e = inv.getArgument(0);
        e.setId(ACTION_ID);
        return e;
      });

      PriceActionEntity result = service.createAction(
          WORKSPACE_ID, OFFER_ID, 50L,
          ActionExecutionMode.LIVE,
          BigDecimal.valueOf(999), BigDecimal.valueOf(800),
          24, false);

      verify(casRepository).casSupersede(99L, ActionStatus.APPROVED, ACTION_ID);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should defer action when existing in-flight action")
    void should_deferAction_when_existingInFlightAction() {
      var existing = actionEntity(ActionStatus.EXECUTING);
      existing.setId(99L);
      when(actionRepository.findActiveByOfferAndModeForUpdate(
          OFFER_ID, ActionExecutionMode.LIVE.name()))
          .thenReturn(Optional.of(existing));
      when(deferredActionRepository.findByMarketplaceOfferIdAndExecutionMode(
          OFFER_ID, ActionExecutionMode.LIVE)).thenReturn(Optional.empty());

      PriceActionEntity result = service.createAction(
          WORKSPACE_ID, OFFER_ID, 50L,
          ActionExecutionMode.LIVE,
          BigDecimal.valueOf(999), BigDecimal.valueOf(800),
          24, false);

      assertThat(result).isNull();
      verify(deferredActionRepository).save(any());
    }
  }

  @Nested
  @DisplayName("casApprove")
  class CasApprove {

    @Test
    @DisplayName("should approve action when in PENDING_APPROVAL")
    void should_approve_when_pendingApproval() {
      var entity = actionEntity(ActionStatus.PENDING_APPROVAL);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casApprove(ACTION_ID, ActionStatus.PENDING_APPROVAL, USER_ID))
          .thenReturn(1);
      when(casRepository.casTransition(ACTION_ID, ActionStatus.APPROVED, ActionStatus.SCHEDULED))
          .thenReturn(1);

      service.casApprove(ACTION_ID, USER_ID);

      verify(casRepository).casApprove(ACTION_ID, ActionStatus.PENDING_APPROVAL, USER_ID);
      verify(outboxService).createEvent(any(), any(), eq(ACTION_ID), any());
      verify(eventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("should throw ConflictException when transition not allowed")
    void should_throwConflict_when_invalidTransition() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.casApprove(ACTION_ID, USER_ID))
          .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("should throw ConflictException when CAS fails (concurrent modification)")
    void should_throwConflict_when_casConflict() {
      var entity = actionEntity(ActionStatus.PENDING_APPROVAL);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casApprove(ACTION_ID, ActionStatus.PENDING_APPROVAL, USER_ID))
          .thenReturn(0);

      assertThatThrownBy(() -> service.casApprove(ACTION_ID, USER_ID))
          .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("should approve action when resuming from ON_HOLD")
    void should_approve_when_onHold() {
      var entity = actionEntity(ActionStatus.ON_HOLD);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casApprove(ACTION_ID, ActionStatus.ON_HOLD, USER_ID))
          .thenReturn(1);
      when(casRepository.casTransition(ACTION_ID, ActionStatus.APPROVED, ActionStatus.SCHEDULED))
          .thenReturn(1);

      service.casApprove(ACTION_ID, USER_ID);

      verify(casRepository).casApprove(ACTION_ID, ActionStatus.ON_HOLD, USER_ID);
    }
  }

  @Nested
  @DisplayName("casReject")
  class CasReject {

    @Test
    @DisplayName("should reject action when in PENDING_APPROVAL")
    void should_reject_when_pendingApproval() {
      var entity = actionEntity(ActionStatus.PENDING_APPROVAL);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casCancel(ACTION_ID, ActionStatus.PENDING_APPROVAL, "Not needed"))
          .thenReturn(1);

      service.casReject(ACTION_ID, "Not needed");

      verify(casRepository).casCancel(ACTION_ID, ActionStatus.PENDING_APPROVAL, "Not needed");
    }

    @Test
    @DisplayName("should throw ConflictException when not in PENDING_APPROVAL")
    void should_throwConflict_when_notPendingApproval() {
      var entity = actionEntity(ActionStatus.APPROVED);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.casReject(ACTION_ID, "reason"))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  @DisplayName("casHold")
  class CasHold {

    @Test
    @DisplayName("should put action on hold when APPROVED")
    void should_hold_when_approved() {
      var entity = actionEntity(ActionStatus.APPROVED);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casHold(ACTION_ID, "Needs review")).thenReturn(1);

      service.casHold(ACTION_ID, "Needs review");

      verify(casRepository).casHold(ACTION_ID, "Needs review");
    }

    @Test
    @DisplayName("should throw ConflictException when transition not allowed")
    void should_throwConflict_when_holdNotAllowed() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.casHold(ACTION_ID, "reason"))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  @DisplayName("casCancel")
  class CasCancel {

    @Test
    @DisplayName("should cancel action when cancellable")
    void should_cancel_when_cancellable() {
      var entity = actionEntity(ActionStatus.APPROVED);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casCancel(ACTION_ID, ActionStatus.APPROVED, "User cancelled"))
          .thenReturn(1);

      service.casCancel(ACTION_ID, "User cancelled");

      verify(casRepository).casCancel(ACTION_ID, ActionStatus.APPROVED, "User cancelled");
    }

    @Test
    @DisplayName("should throw ConflictException when not cancellable (EXECUTING)")
    void should_throwConflict_when_notCancellable() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.casCancel(ACTION_ID, "reason"))
          .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("should throw ConflictException when CAS fails")
    void should_throwConflict_when_cancelCasConflict() {
      var entity = actionEntity(ActionStatus.APPROVED);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casCancel(ACTION_ID, ActionStatus.APPROVED, "reason")).thenReturn(0);

      assertThatThrownBy(() -> service.casCancel(ACTION_ID, "reason"))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  @DisplayName("casSucceed")
  class CasSucceed {

    @Test
    @DisplayName("should succeed action and publish ActionCompletedEvent")
    void should_succeed_when_validTransition() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casSucceed(ACTION_ID, ActionStatus.EXECUTING,
          ActionReconciliationSource.AUTO, null)).thenReturn(1);

      service.casSucceed(ACTION_ID, ActionStatus.EXECUTING,
          ActionReconciliationSource.AUTO, null);

      verify(casRepository).casSucceed(ACTION_ID, ActionStatus.EXECUTING,
          ActionReconciliationSource.AUTO, null);

      var captor = ArgumentCaptor.forClass(ActionCompletedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().actionId()).isEqualTo(ACTION_ID);
    }

    @Test
    @DisplayName("should throw ConflictException when transition not allowed")
    void should_throwConflict_when_invalidTransition() {
      var entity = actionEntity(ActionStatus.PENDING_APPROVAL);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.casSucceed(ACTION_ID, ActionStatus.PENDING_APPROVAL,
          ActionReconciliationSource.AUTO, null))
          .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("should throw ConflictException when CAS fails")
    void should_throwConflict_when_casConflict() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casSucceed(ACTION_ID, ActionStatus.EXECUTING,
          ActionReconciliationSource.AUTO, null)).thenReturn(0);

      assertThatThrownBy(() -> service.casSucceed(ACTION_ID, ActionStatus.EXECUTING,
          ActionReconciliationSource.AUTO, null))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  @DisplayName("casFail")
  class CasFail {

    @Test
    @DisplayName("should fail action and publish ActionFailedEvent")
    void should_fail_when_validState() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casFail(ACTION_ID, ActionStatus.EXECUTING, 2)).thenReturn(1);

      service.casFail(ACTION_ID, ActionStatus.EXECUTING, 2,
          ErrorClassification.NON_RETRIABLE, "Provider rejected");

      var captor = ArgumentCaptor.forClass(ActionFailedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().attemptCount()).isEqualTo(2);
      assertThat(captor.getValue().lastErrorClassification())
          .isEqualTo(ErrorClassification.NON_RETRIABLE);
    }

    @Test
    @DisplayName("should throw ConflictException when CAS fails")
    void should_throwConflict_when_casConflict() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));
      when(casRepository.casFail(ACTION_ID, ActionStatus.EXECUTING, 1)).thenReturn(0);

      assertThatThrownBy(() -> service.casFail(ACTION_ID, ActionStatus.EXECUTING, 1,
          ErrorClassification.NON_RETRIABLE, "error"))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  @DisplayName("casScheduleRetry")
  class CasScheduleRetry {

    @Test
    @DisplayName("should schedule retry with backoff and create outbox event")
    void should_scheduleRetry_when_valid() {
      when(properties.getMinBackoff()).thenReturn(Duration.ofSeconds(5));
      when(properties.getMaxBackoff()).thenReturn(Duration.ofMinutes(5));
      when(properties.getBackoffMultiplier()).thenReturn(2);
      when(casRepository.casRetryScheduled(eq(ACTION_ID), eq(1), any())).thenReturn(1);

      service.casScheduleRetry(ACTION_ID, 1);

      verify(casRepository).casRetryScheduled(eq(ACTION_ID), eq(1), any());
      verify(outboxService).createEvent(any(), any(), eq(ACTION_ID), any());
    }

    @Test
    @DisplayName("should skip when CAS fails")
    void should_skip_when_casConflict() {
      when(properties.getMinBackoff()).thenReturn(Duration.ofSeconds(5));
      when(properties.getMaxBackoff()).thenReturn(Duration.ofMinutes(5));
      when(properties.getBackoffMultiplier()).thenReturn(2);
      when(casRepository.casRetryScheduled(eq(ACTION_ID), eq(1), any())).thenReturn(0);

      service.casScheduleRetry(ACTION_ID, 1);

      verify(outboxService, never()).createEvent(any(), any(), anyLong(), any());
    }
  }

  @Nested
  @DisplayName("casReconciliationPending")
  class CasReconciliationPending {

    @Test
    @DisplayName("should transition to RECONCILIATION_PENDING and create outbox event")
    void should_transitionAndCreateEvent_when_valid() {
      when(casRepository.casTransition(ACTION_ID, ActionStatus.EXECUTING,
          ActionStatus.RECONCILIATION_PENDING)).thenReturn(1);
      when(properties.getReconciliation())
          .thenReturn(new ExecutionProperties.Reconciliation(
              Duration.ofSeconds(30), 2, 3, Duration.ofMinutes(10)));

      service.casReconciliationPending(ACTION_ID);

      verify(outboxService).createEvent(any(), any(), eq(ACTION_ID), any());
    }

    @Test
    @DisplayName("should skip when CAS fails")
    void should_skip_when_casConflict() {
      when(casRepository.casTransition(ACTION_ID, ActionStatus.EXECUTING,
          ActionStatus.RECONCILIATION_PENDING)).thenReturn(0);

      service.casReconciliationPending(ACTION_ID);

      verify(outboxService, never()).createEvent(any(), any(), anyLong(), any());
    }
  }

  @Nested
  @DisplayName("retryFailed")
  class RetryFailed {

    @Test
    @DisplayName("should throw ConflictException when action is not FAILED")
    void should_throwConflict_when_notFailed() {
      var entity = actionEntity(ActionStatus.EXECUTING);
      when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.retryFailed(ACTION_ID, "test reason"))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  @DisplayName("casClaim")
  class CasClaim {

    @Test
    @DisplayName("should transition SCHEDULED to EXECUTING")
    void should_claim_when_scheduled() {
      when(casRepository.casTransition(ACTION_ID, ActionStatus.SCHEDULED,
          ActionStatus.EXECUTING)).thenReturn(1);

      service.casClaim(ACTION_ID);

      verify(casRepository).casTransition(ACTION_ID, ActionStatus.SCHEDULED,
          ActionStatus.EXECUTING);
    }
  }

  private PriceActionEntity actionEntity(ActionStatus status) {
    var entity = new PriceActionEntity();
    entity.setId(ACTION_ID);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setMarketplaceOfferId(OFFER_ID);
    entity.setPriceDecisionId(50L);
    entity.setExecutionMode(ActionExecutionMode.LIVE);
    entity.setStatus(status);
    entity.setTargetPrice(BigDecimal.valueOf(999));
    entity.setCurrentPriceAtCreation(BigDecimal.valueOf(800));
    entity.setMaxAttempts(3);
    entity.setAttemptCount(0);
    entity.setApprovalTimeoutHours(24);
    return entity;
  }
}
