package io.datapulse.pricing.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PricingRunAlertListenerTest {

  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private PricingRunAlertListener listener;

  @Nested
  @DisplayName("onPricingRunCompleted")
  class OnPricingRunCompleted {

    @Test
    @DisplayName("should publish CRITICAL alert when run FAILED")
    void should_publish_alert_when_failed() {
      var event = new PricingRunCompletedEvent(
          55L, 1L, 10L, 0, 5, 0, RunStatus.FAILED);

      listener.onPricingRunCompleted(event);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AlertTriggeredEvent alert = captor.getValue();
      assertThat(alert.workspaceId()).isEqualTo(1L);
      assertThat(alert.connectionId()).isEqualTo(10L);
      assertThat(alert.severity()).isEqualTo("CRITICAL");
      assertThat(alert.title()).isEqualTo(MessageCodes.ALERT_PRICING_RUN_FAILED_TITLE);
      assertThat(alert.details()).contains("55");
    }

    @Test
    @DisplayName("should skip when run COMPLETED")
    void should_skip_when_completed() {
      var event = new PricingRunCompletedEvent(
          55L, 1L, 10L, 3, 2, 1, RunStatus.COMPLETED);

      listener.onPricingRunCompleted(event);

      verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("should skip when run CANCELLED")
    void should_skip_when_cancelled() {
      var event = new PricingRunCompletedEvent(
          55L, 1L, 10L, 0, 0, 0, RunStatus.CANCELLED);

      listener.onPricingRunCompleted(event);

      verifyNoInteractions(eventPublisher);
    }
  }
}
