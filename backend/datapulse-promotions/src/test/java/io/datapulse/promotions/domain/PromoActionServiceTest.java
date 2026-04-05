package io.datapulse.promotions.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.BulkPromoActionRequest;
import io.datapulse.promotions.api.BulkPromoActionResponse;
import io.datapulse.promotions.api.PromoActionMapper;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionQueryRepository;
import io.datapulse.promotions.persistence.PromoActionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.datapulse.platform.audit.AuditPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoActionServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long ACTION_ID = 100L;

  @Mock
  private PromoActionRepository actionRepository;
  @Mock
  private PromoActionQueryRepository actionQueryRepository;
  @Mock
  private PromoActionMapper actionMapper;
  @Mock
  private AuditPublisher auditPublisher;

  @InjectMocks
  private PromoActionService service;

  @Nested
  @DisplayName("approveAction")
  class ApproveAction {

    @Test
    void should_approve_when_pending_approval() {
      var action = buildAction(PromoActionStatus.PENDING_APPROVAL);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));
      when(actionRepository.casUpdateStatus(ACTION_ID,
          PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.APPROVED))
          .thenReturn(1);

      service.approveAction(ACTION_ID, WORKSPACE_ID);

      verify(actionRepository).casUpdateStatus(
          ACTION_ID, PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.APPROVED);
    }

    @Test
    void should_throw_bad_request_when_not_pending() {
      var action = buildAction(PromoActionStatus.APPROVED);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));

      assertThatThrownBy(() -> service.approveAction(ACTION_ID, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_conflict_when_cas_fails() {
      var action = buildAction(PromoActionStatus.PENDING_APPROVAL);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));
      when(actionRepository.casUpdateStatus(ACTION_ID,
          PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.APPROVED))
          .thenReturn(0);

      assertThatThrownBy(() -> service.approveAction(ACTION_ID, WORKSPACE_ID))
          .isInstanceOf(ConflictException.class);
    }

    @Test
    void should_throw_not_found_when_action_missing() {
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.approveAction(ACTION_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("rejectAction")
  class RejectAction {

    @Test
    void should_reject_and_set_reason_when_pending() {
      var action = buildAction(PromoActionStatus.PENDING_APPROVAL);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));
      when(actionRepository.casUpdateStatus(ACTION_ID,
          PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.CANCELLED))
          .thenReturn(1);

      service.rejectAction(ACTION_ID, "Too risky", WORKSPACE_ID);

      assertThat(action.getCancelReason()).isEqualTo("Too risky");
      verify(actionRepository).save(action);
    }

    @Test
    void should_throw_when_not_pending() {
      var action = buildAction(PromoActionStatus.EXECUTING);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));

      assertThatThrownBy(() -> service.rejectAction(ACTION_ID, "reason", WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("cancelAction")
  class CancelAction {

    @Test
    void should_cancel_pending_approval_action() {
      var action = buildAction(PromoActionStatus.PENDING_APPROVAL);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));
      when(actionRepository.casUpdateStatus(ACTION_ID,
          PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.CANCELLED))
          .thenReturn(1);

      service.cancelAction(ACTION_ID, "Changed mind", WORKSPACE_ID);

      assertThat(action.getCancelReason()).isEqualTo("Changed mind");
    }

    @Test
    void should_cancel_approved_action() {
      var action = buildAction(PromoActionStatus.APPROVED);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));
      when(actionRepository.casUpdateStatus(ACTION_ID,
          PromoActionStatus.APPROVED, PromoActionStatus.CANCELLED))
          .thenReturn(1);

      service.cancelAction(ACTION_ID, "No longer needed", WORKSPACE_ID);

      verify(actionRepository).casUpdateStatus(
          ACTION_ID, PromoActionStatus.APPROVED, PromoActionStatus.CANCELLED);
    }

    @Test
    void should_throw_when_terminal_status() {
      var action = buildAction(PromoActionStatus.SUCCEEDED);
      when(actionRepository.findByIdAndWorkspaceId(ACTION_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(action));

      assertThatThrownBy(() -> service.cancelAction(ACTION_ID, "reason", WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("bulkApprove")
  class BulkApprove {

    @Test
    void should_approve_all_pending_actions() {
      var a1 = buildAction(PromoActionStatus.PENDING_APPROVAL);
      a1.setId(1L);
      var a2 = buildAction(PromoActionStatus.PENDING_APPROVAL);
      a2.setId(2L);

      when(actionRepository.findAllByIdInAndWorkspaceId(List.of(1L, 2L), WORKSPACE_ID))
          .thenReturn(List.of(a1, a2));
      when(actionRepository.casUpdateStatus(eq(1L), any(), any())).thenReturn(1);
      when(actionRepository.casUpdateStatus(eq(2L), any(), any())).thenReturn(1);

      BulkPromoActionResponse result = service.bulkApprove(
          new BulkPromoActionRequest(List.of(1L, 2L)), WORKSPACE_ID);

      assertThat(result.succeeded()).containsExactly(1L, 2L);
      assertThat(result.failed()).isEmpty();
    }

    @Test
    void should_report_non_pending_as_failed() {
      var a1 = buildAction(PromoActionStatus.PENDING_APPROVAL);
      a1.setId(1L);
      var a2 = buildAction(PromoActionStatus.EXECUTING);
      a2.setId(2L);

      when(actionRepository.findAllByIdInAndWorkspaceId(List.of(1L, 2L), WORKSPACE_ID))
          .thenReturn(List.of(a1, a2));
      when(actionRepository.casUpdateStatus(eq(1L), any(), any())).thenReturn(1);

      BulkPromoActionResponse result = service.bulkApprove(
          new BulkPromoActionRequest(List.of(1L, 2L)), WORKSPACE_ID);

      assertThat(result.succeeded()).containsExactly(1L);
      assertThat(result.failed()).hasSize(1);
      assertThat(result.failed().get(0).actionId()).isEqualTo(2L);
    }

    @Test
    void should_report_cas_conflict_as_failed() {
      var a1 = buildAction(PromoActionStatus.PENDING_APPROVAL);
      a1.setId(1L);

      when(actionRepository.findAllByIdInAndWorkspaceId(List.of(1L), WORKSPACE_ID))
          .thenReturn(List.of(a1));
      when(actionRepository.casUpdateStatus(eq(1L), any(), any())).thenReturn(0);

      BulkPromoActionResponse result = service.bulkApprove(
          new BulkPromoActionRequest(List.of(1L)), WORKSPACE_ID);

      assertThat(result.succeeded()).isEmpty();
      assertThat(result.failed()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("bulkReject")
  class BulkReject {

    @Test
    void should_reject_and_save_reason() {
      var a1 = buildAction(PromoActionStatus.PENDING_APPROVAL);
      a1.setId(1L);

      when(actionRepository.findAllByIdInAndWorkspaceId(List.of(1L), WORKSPACE_ID))
          .thenReturn(List.of(a1));
      when(actionRepository.casUpdateStatus(eq(1L), any(), any())).thenReturn(1);

      BulkPromoActionResponse result = service.bulkReject(
          new BulkPromoActionRequest(List.of(1L)), "bulk reason", WORKSPACE_ID);

      assertThat(result.succeeded()).containsExactly(1L);
      assertThat(a1.getCancelReason()).isEqualTo("bulk reason");
      verify(actionRepository).save(a1);
    }
  }

  private PromoActionEntity buildAction(PromoActionStatus status) {
    var entity = new PromoActionEntity();
    entity.setId(ACTION_ID);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setStatus(status);
    entity.setActionType(PromoActionType.ACTIVATE);
    entity.setPromoDecisionId(1L);
    entity.setCanonicalPromoCampaignId(1L);
    entity.setMarketplaceOfferId(1L);
    entity.setAttemptCount(0);
    entity.setExecutionMode(PromoExecutionMode.LIVE);
    return entity;
  }
}
