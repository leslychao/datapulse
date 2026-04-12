package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
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
    scheduler.scheduleAction(1L, 100L, new BigDecimal("999"), new BigDecimal("800"),
        ExecutionMode.RECOMMENDATION, 5L, 10L, 72);

    verify(outboxService, never()).createEvent(any(), any(), anyLong(), any());
  }

  @Test
  @DisplayName("publishes PRICING_ACTION_REQUESTED for SEMI_AUTO with autoApprove=false")
  @SuppressWarnings("unchecked")
  void should_publishEvent_when_semiAutoMode() {
    scheduler.scheduleAction(1L, 100L, new BigDecimal("1200"), new BigDecimal("1000"),
        ExecutionMode.SEMI_AUTO, 5L, 10L, 48);

    verify(outboxService).createEvent(
        eq(OutboxEventType.PRICING_ACTION_REQUESTED), eq("price_decision"),
        eq(1L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload)
        .containsEntry("autoApprove", false)
        .containsEntry("executionMode", "SEMI_AUTO")
        .containsEntry("targetPrice", "1200")
        .containsEntry("approvalTimeoutHours", 48);
  }

  @Test
  @DisplayName("publishes PRICING_ACTION_REQUESTED for FULL_AUTO with autoApprove=true")
  @SuppressWarnings("unchecked")
  void should_publishEvent_when_fullAutoMode() {
    scheduler.scheduleAction(1L, 100L, new BigDecimal("900"), new BigDecimal("1000"),
        ExecutionMode.FULL_AUTO, 5L, 10L, 72);

    verify(outboxService).createEvent(
        eq(OutboxEventType.PRICING_ACTION_REQUESTED), eq("price_decision"),
        eq(1L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload)
        .containsEntry("autoApprove", true)
        .containsEntry("executionMode", "FULL_AUTO");
  }

  @Test
  @DisplayName("publishes PRICING_ACTION_REQUESTED for SIMULATED with autoApprove=true")
  @SuppressWarnings("unchecked")
  void should_publishEvent_when_simulatedMode() {
    scheduler.scheduleAction(1L, 100L, new BigDecimal("800"), new BigDecimal("900"),
        ExecutionMode.SIMULATED, 5L, 10L, 72);

    verify(outboxService).createEvent(
        eq(OutboxEventType.PRICING_ACTION_REQUESTED), eq("price_decision"),
        eq(1L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload)
        .containsEntry("autoApprove", true)
        .containsEntry("executionMode", "SIMULATED");
  }

  @Test
  @DisplayName("payload contains all required fields")
  @SuppressWarnings("unchecked")
  void should_includeAllFields_when_actionScheduled() {
    scheduler.scheduleAction(42L, 200L, new BigDecimal("1500.50"), new BigDecimal("1400"),
        ExecutionMode.FULL_AUTO, 5L, 7L, 72);

    verify(outboxService).createEvent(any(), any(), eq(42L), payloadCaptor.capture());

    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload)
        .containsEntry("decisionId", 42L)
        .containsEntry("marketplaceOfferId", 200L)
        .containsEntry("targetPrice", "1500.50")
        .containsEntry("currentPrice", "1400")
        .containsEntry("connectionId", 5L)
        .containsEntry("workspaceId", 7L)
        .containsEntry("approvalTimeoutHours", 72)
        .containsEntry("autoApprove", true);
  }
}
