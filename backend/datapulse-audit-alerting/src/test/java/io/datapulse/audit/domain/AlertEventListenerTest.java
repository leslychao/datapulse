package io.datapulse.audit.domain;

import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.persistence.AlertEventRepository;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEventListenerTest {

  @Mock
  private AlertEventRepository alertEventRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private AlertEventListener listener;

  @Nested
  @DisplayName("onAlertTriggered")
  class OnAlertTriggered {

    @Test
    @DisplayName("should_insert_event_and_publish_created_event_when_triggered")
    void should_insert_event_and_publish_created_event_when_triggered() {
      var event = new AlertTriggeredEvent(
          1L, 10L, "CRITICAL", "Sync failed",
          "{\"reason\":\"timeout\"}", true);

      when(alertEventRepository.insertEventDriven(
          eq(1L), eq(10L), eq("CRITICAL"), eq("Sync failed"),
          anyString(), eq(true)))
          .thenReturn(42L);

      listener.onAlertTriggered(event);

      verify(alertEventRepository).insertEventDriven(
          1L, 10L, "CRITICAL", "Sync failed",
          "{\"reason\":\"timeout\"}", true);

      ArgumentCaptor<AlertEventCreatedEvent> captor =
          ArgumentCaptor.forClass(AlertEventCreatedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AlertEventCreatedEvent published = captor.getValue();
      assertThat(published.alertEventId()).isEqualTo(42L);
      assertThat(published.workspaceId()).isEqualTo(1L);
      assertThat(published.connectionId()).isEqualTo(10L);
      assertThat(published.ruleType()).isNull();
      assertThat(published.severity()).isEqualTo("CRITICAL");
      assertThat(published.status()).isEqualTo("OPEN");
      assertThat(published.blocksAutomation()).isTrue();
    }

    @Test
    @DisplayName("should_handle_null_connection_id_when_workspace_wide_alert")
    void should_handle_null_connection_id_when_workspace_wide_alert() {
      var event = new AlertTriggeredEvent(
          1L, null, "WARNING", "General issue",
          null, false);

      when(alertEventRepository.insertEventDriven(
          eq(1L), eq(null), eq("WARNING"), eq("General issue"),
          eq(null), eq(false)))
          .thenReturn(43L);

      listener.onAlertTriggered(event);

      verify(alertEventRepository).insertEventDriven(
          1L, null, "WARNING", "General issue", null, false);
    }

    @Test
    @DisplayName("should_not_throw_when_repository_fails")
    void should_not_throw_when_repository_fails() {
      var event = new AlertTriggeredEvent(
          1L, 10L, "CRITICAL", "title", null, false);

      doThrow(new RuntimeException("DB down"))
          .when(alertEventRepository).insertEventDriven(
              anyLong(), any(), anyString(), anyString(), any(), anyBoolean());

      assertThatCode(() -> listener.onAlertTriggered(event))
          .doesNotThrowAnyException();

      verify(eventPublisher, never()).publishEvent(any());
    }
  }
}
