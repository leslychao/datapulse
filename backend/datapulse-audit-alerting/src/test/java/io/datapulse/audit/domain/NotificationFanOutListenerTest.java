package io.datapulse.audit.domain;

import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFanOutListenerTest {

  @Mock
  private NotificationService notificationService;
  @Mock
  private UserNotificationStompPublisher userNotificationStompPublisher;
  @Mock
  private WorkspaceAlertTopicStompPublisher workspaceAlertTopicStompPublisher;

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

      verify(workspaceAlertTopicStompPublisher).publishAlertCreated(event);
      verify(userNotificationStompPublisher)
          .publish(
              argThat(
                  (List<long[]> pairs) ->
                      pairs.size() == 2
                          && pairs.get(0)[0] == 10L
                          && pairs.get(0)[1] == 100L
                          && pairs.get(1)[0] == 20L
                          && pairs.get(1)[1] == 101L),
              eq("ALERT"),
              eq("Stale data detected"),
              isNull(),
              eq("WARNING"),
              eq(42L));
    }

    @Test
    @DisplayName("should_not_throw_when_messaging_fails")
    void should_not_throw_when_messaging_fails() {
      var event = new AlertEventCreatedEvent(
          42L, 1L, 10L, null,
          "CRITICAL", "Error", "OPEN", true);

      doThrow(new RuntimeException("WS down"))
          .when(workspaceAlertTopicStompPublisher).publishAlertCreated(any());

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

      verify(workspaceAlertTopicStompPublisher).publishAlertResolved(event);
    }

    @Test
    @DisplayName("should_send_auto_resolved_status_when_auto_resolution")
    void should_send_auto_resolved_status_when_auto_resolution() {
      var event = new AlertResolvedEvent(
          42L, 1L, 10L, "INFO", "Cleared", "AUTO");

      listener.onAlertResolved(event);

      verify(workspaceAlertTopicStompPublisher).publishAlertResolved(event);
    }

    @Test
    @DisplayName("should_not_throw_when_messaging_fails")
    void should_not_throw_when_messaging_fails() {
      var event = new AlertResolvedEvent(
          42L, 1L, 10L, "WARNING", "Test", "MANUAL");

      doThrow(new RuntimeException("WS down"))
          .when(workspaceAlertTopicStompPublisher).publishAlertResolved(any());

      assertThatCode(() -> listener.onAlertResolved(event))
          .doesNotThrowAnyException();
    }
  }
}
