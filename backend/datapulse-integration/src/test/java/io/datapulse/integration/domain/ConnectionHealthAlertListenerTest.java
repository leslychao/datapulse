package io.datapulse.integration.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.integration.domain.event.ConnectionHealthDegradedEvent;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConnectionHealthAlertListenerTest {

  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private ConnectionHealthAlertListener listener;

  @Test
  @DisplayName("should publish WARNING AlertTriggeredEvent with connection details")
  void should_publish_warning_alert() {
    var event = new ConnectionHealthDegradedEvent(
        10L, 1L, "WB", 5, "HTTP_502");

    listener.onHealthDegraded(event);

    ArgumentCaptor<AlertTriggeredEvent> captor =
        ArgumentCaptor.forClass(AlertTriggeredEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    AlertTriggeredEvent alert = captor.getValue();
    assertThat(alert.workspaceId()).isEqualTo(1L);
    assertThat(alert.connectionId()).isEqualTo(10L);
    assertThat(alert.severity()).isEqualTo("WARNING");
    assertThat(alert.title()).isEqualTo(
        MessageCodes.ALERT_CONNECTION_HEALTH_DEGRADED_TITLE);
    assertThat(alert.details()).contains("WB");
    assertThat(alert.details()).contains("5");
  }

  @Test
  @DisplayName("should not throw when publisher fails")
  void should_not_throw_when_publisher_fails() {
    var event = new ConnectionHealthDegradedEvent(
        10L, 1L, "OZON", 3, null);

    doThrow(new RuntimeException("down"))
        .when(eventPublisher).publishEvent(any(AlertTriggeredEvent.class));

    assertThatCode(() -> listener.onHealthDegraded(event))
        .doesNotThrowAnyException();
  }
}
