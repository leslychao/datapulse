package io.datapulse.execution.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.execution.domain.event.ActionFailedEvent;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActionAlertListenerTest {

  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private ActionAlertListener listener;

  @Nested
  @DisplayName("onActionFailed")
  class OnActionFailed {

    @Test
    @DisplayName("should publish CRITICAL AlertTriggeredEvent")
    void should_publish_critical_alert() {
      var event = new ActionFailedEvent(
          42L, 1L, 100L, ActionExecutionMode.LIVE,
          BigDecimal.valueOf(999), 3, ErrorClassification.NON_RETRIABLE,
          "Provider rejected");

      listener.onActionFailed(event);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AlertTriggeredEvent alert = captor.getValue();
      assertThat(alert.workspaceId()).isEqualTo(1L);
      assertThat(alert.connectionId()).isNull();
      assertThat(alert.severity()).isEqualTo("CRITICAL");
      assertThat(alert.title()).isEqualTo(MessageCodes.ALERT_ACTION_FAILED_TITLE);
      assertThat(alert.blocksAutomation()).isFalse();
      assertThat(alert.details()).contains("42");
      assertThat(alert.details()).contains("NON_RETRIABLE");
    }

    @Test
    @DisplayName("should not throw when publisher fails")
    void should_not_throw_when_publisher_fails() {
      var event = new ActionFailedEvent(
          1L, 1L, 1L, ActionExecutionMode.LIVE,
          BigDecimal.ONE, 1, ErrorClassification.RETRIABLE_TRANSIENT, "timeout");

      doThrow(new RuntimeException("down"))
          .when(eventPublisher).publishEvent(any(AlertTriggeredEvent.class));

      assertThatCode(() -> listener.onActionFailed(event))
          .doesNotThrowAnyException();
    }
  }
}
