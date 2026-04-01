package io.datapulse.audit.domain;

import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFanOutListenerTest {

  @Mock
  private NotificationService notificationService;
  @Mock
  private SimpMessagingTemplate messagingTemplate;

  @InjectMocks
  private NotificationFanOutListener listener;

  @Nested
  @DisplayName("onAlertCreated")
  class OnAlertCreated {

    @Test
    @DisplayName("should_push_to_workspace_topic_and_fan_out_notifications")
    void should_push_to_workspace_topic_and_fan_out_notifications() {
      var event = new AlertEventCreatedEvent(
          42L, 1L, 10L, "STALE_DATA",
          "WARNING", "Stale data detected", "OPEN", false);

      when(notificationService.fanOut(
          eq(1L), eq(42L), eq("ALERT"),
          eq("Stale data detected"), eq(null), eq("WARNING")))
          .thenReturn(List.of(new long[]{10L, 100L}, new long[]{20L, 101L}));

      listener.onAlertCreated(event);

      verify(messagingTemplate).convertAndSend(
          eq("/topic/workspace/1/alerts"), any(Map.class));
      verify(messagingTemplate, times(2))
          .convertAndSendToUser(anyString(), eq("/queue/notifications"), any(Map.class));
    }

    @Test
    @DisplayName("should_not_throw_when_messaging_fails")
    void should_not_throw_when_messaging_fails() {
      var event = new AlertEventCreatedEvent(
          42L, 1L, 10L, null,
          "CRITICAL", "Error", "OPEN", true);

      doThrow(new RuntimeException("WS down"))
          .when(messagingTemplate).convertAndSend(anyString(), any(Map.class));

      assertThatCode(() -> listener.onAlertCreated(event))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("onAlertResolved")
  class OnAlertResolved {

    @Test
    @DisplayName("should_send_resolved_status_when_manual_resolution")
    void should_send_resolved_status_when_manual_resolution() {
      var event = new AlertResolvedEvent(
          42L, 1L, 10L, "WARNING", "Test alert", "MANUAL");

      listener.onAlertResolved(event);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor =
          ArgumentCaptor.forClass(Map.class);
      verify(messagingTemplate).convertAndSend(
          eq("/topic/workspace/1/alerts"), captor.capture());

      assertThat(captor.getValue().get("status")).isEqualTo("RESOLVED");
      assertThat(captor.getValue().get("resolvedReason")).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("should_send_auto_resolved_status_when_auto_resolution")
    void should_send_auto_resolved_status_when_auto_resolution() {
      var event = new AlertResolvedEvent(
          42L, 1L, 10L, "INFO", "Cleared", "AUTO");

      listener.onAlertResolved(event);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor =
          ArgumentCaptor.forClass(Map.class);
      verify(messagingTemplate).convertAndSend(
          eq("/topic/workspace/1/alerts"), captor.capture());

      assertThat(captor.getValue().get("status")).isEqualTo("AUTO_RESOLVED");
    }

    @Test
    @DisplayName("should_not_throw_when_messaging_fails")
    void should_not_throw_when_messaging_fails() {
      var event = new AlertResolvedEvent(
          42L, 1L, 10L, "WARNING", "Test", "MANUAL");

      doThrow(new RuntimeException("WS down"))
          .when(messagingTemplate).convertAndSend(anyString(), any(Map.class));

      assertThatCode(() -> listener.onAlertResolved(event))
          .doesNotThrowAnyException();
    }
  }
}
