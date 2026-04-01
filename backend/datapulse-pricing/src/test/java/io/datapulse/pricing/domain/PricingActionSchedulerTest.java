package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
class PricingActionSchedulerTest {

  @Mock private OutboxService outboxService;

  @InjectMocks
  private PricingActionScheduler scheduler;

  @Captor
  private ArgumentCaptor<Map<String, Object>> payloadCaptor;

  @Test
  @DisplayName("does nothing for RECOMMENDATION mode — no action created")
  void should_noOp_when_recommendationMode() {
    scheduler.scheduleAction(1L, 100L, new BigDecimal("999"), ExecutionMode.RECOMMENDATION, 10L);

    verify(outboxService, never()).createEvent(any(), any(), anyLong(), any());
  }

  @Test
  @DisplayName("creates PENDING_APPROVAL action for SEMI_AUTO mode")
  @SuppressWarnings("unchecked")
  void should_createPendingApproval_when_semiAutoMode() {
    scheduler.scheduleAction(1L, 100L, new BigDecimal("1200"), ExecutionMode.SEMI_AUTO, 10L);

    verify(outboxService).createEvent(
        eq(OutboxEventType.PRICE_ACTION_EXECUTE), eq("price_decision"),
        eq(1L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload).containsEntry("actionStatus", "PENDING_APPROVAL");
    assertThat(payload).containsEntry("executionMode", "LIVE");
    assertThat(payload).containsEntry("targetPrice", "1200");
  }

  @Test
  @DisplayName("creates APPROVED action for FULL_AUTO mode")
  @SuppressWarnings("unchecked")
  void should_createApproved_when_fullAutoMode() {
    scheduler.scheduleAction(1L, 100L, new BigDecimal("900"), ExecutionMode.FULL_AUTO, 10L);

    verify(outboxService).createEvent(
        eq(OutboxEventType.PRICE_ACTION_EXECUTE), eq("price_decision"),
        eq(1L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload).containsEntry("actionStatus", "APPROVED");
    assertThat(payload).containsEntry("executionMode", "LIVE");
  }

  @Test
  @DisplayName("creates APPROVED action with SIMULATED mode for simulation")
  @SuppressWarnings("unchecked")
  void should_createApprovedSimulated_when_simulatedMode() {
    scheduler.scheduleAction(1L, 100L, new BigDecimal("800"), ExecutionMode.SIMULATED, 10L);

    verify(outboxService).createEvent(
        eq(OutboxEventType.PRICE_ACTION_EXECUTE), eq("price_decision"),
        eq(1L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload).containsEntry("actionStatus", "APPROVED");
    assertThat(payload).containsEntry("executionMode", "SIMULATED");
  }

  @Test
  @DisplayName("payload contains all required fields")
  @SuppressWarnings("unchecked")
  void should_includeAllFields_when_actionScheduled() {
    scheduler.scheduleAction(42L, 200L, new BigDecimal("1500.50"), ExecutionMode.FULL_AUTO, 7L);

    verify(outboxService).createEvent(any(), any(), eq(42L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload)
        .containsEntry("decisionId", 42L)
        .containsEntry("marketplaceOfferId", 200L)
        .containsEntry("targetPrice", "1500.50")
        .containsEntry("workspaceId", 7L);
  }
}
