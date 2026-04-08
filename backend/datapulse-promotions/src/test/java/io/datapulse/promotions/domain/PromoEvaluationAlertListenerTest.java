package io.datapulse.promotions.domain;

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
class PromoEvaluationAlertListenerTest {

  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private PromoEvaluationAlertListener listener;

  @Nested
  @DisplayName("onPromoEvaluationCompleted")
  class OnPromoEvaluationCompleted {

    @Test
    @DisplayName("should publish CRITICAL alert when evaluation FAILED")
    void should_publish_alert_when_failed() {
      var event = new PromoEvaluationCompletedEvent(
          77L, 1L, 10L, 0, 0, 0, 0, PromoRunStatus.FAILED);

      listener.onPromoEvaluationCompleted(event);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AlertTriggeredEvent alert = captor.getValue();
      assertThat(alert.workspaceId()).isEqualTo(1L);
      assertThat(alert.connectionId()).isEqualTo(10L);
      assertThat(alert.severity()).isEqualTo("CRITICAL");
      assertThat(alert.title()).isEqualTo(
          MessageCodes.ALERT_PROMO_EVALUATION_FAILED_TITLE);
      assertThat(alert.details()).contains("77");
    }

    @Test
    @DisplayName("should skip when evaluation COMPLETED")
    void should_skip_when_completed() {
      var event = new PromoEvaluationCompletedEvent(
          77L, 1L, 10L, 5, 2, 1, 0, PromoRunStatus.COMPLETED);

      listener.onPromoEvaluationCompleted(event);

      verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("should skip when evaluation COMPLETED_WITH_ERRORS")
    void should_skip_when_completed_with_errors() {
      var event = new PromoEvaluationCompletedEvent(
          77L, 1L, 10L, 3, 1, 0, 0, PromoRunStatus.COMPLETED_WITH_ERRORS);

      listener.onPromoEvaluationCompleted(event);

      verifyNoInteractions(eventPublisher);
    }
  }
}
