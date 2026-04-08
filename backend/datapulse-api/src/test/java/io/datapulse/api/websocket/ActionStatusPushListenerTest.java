package io.datapulse.api.websocket;

import io.datapulse.audit.domain.NotificationService;
import io.datapulse.audit.domain.UserNotificationStompPublisher;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.event.ActionCompletedEvent;
import io.datapulse.execution.domain.event.ActionCreatedEvent;
import io.datapulse.execution.domain.event.ActionFailedEvent;
import io.datapulse.execution.domain.ActionReconciliationSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionStatusPushListenerTest {

  @Mock
  private WorkspaceTopicStompPublisher stompPublisher;
  @Mock
  private NotificationService notificationService;
  @Mock
  private UserNotificationStompPublisher userNotificationStompPublisher;

  @InjectMocks
  private ActionStatusPushListener listener;

  @Nested
  @DisplayName("onActionCreated")
  class OnActionCreated {

    @Test
    @DisplayName("should push STOMP update for auto-approved action")
    void should_push_stomp_for_approved() {
      var event = new ActionCreatedEvent(
          42L, 1L, 100L, 200L,
          ActionExecutionMode.LIVE, ActionStatus.APPROVED,
          BigDecimal.valueOf(999), BigDecimal.valueOf(1000));

      listener.onActionCreated(event);

      verify(stompPublisher).publishActionStatusUpdate(
          1L, 42L, "APPROVED", "LIVE");
      verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("should push STOMP and send approval notification for pending action")
    void should_send_approval_notification_for_pending() {
      var event = new ActionCreatedEvent(
          42L, 1L, 100L, 200L,
          ActionExecutionMode.LIVE, ActionStatus.PENDING_APPROVAL,
          BigDecimal.valueOf(999), BigDecimal.valueOf(1000));

      when(notificationService.fanOut(
          eq(1L), isNull(), eq("APPROVAL_REQUEST"),
          eq(MessageCodes.NOTIFICATION_APPROVAL_REQUEST_TITLE),
          eq(MessageCodes.NOTIFICATION_APPROVAL_REQUEST_BODY),
          eq("INFO")))
          .thenReturn(List.of(new long[]{10L, 500L}));

      listener.onActionCreated(event);

      verify(stompPublisher).publishActionStatusUpdate(
          1L, 42L, "PENDING_APPROVAL", "LIVE");
      verify(notificationService).fanOut(
          eq(1L), isNull(), eq("APPROVAL_REQUEST"),
          eq(MessageCodes.NOTIFICATION_APPROVAL_REQUEST_TITLE),
          eq(MessageCodes.NOTIFICATION_APPROVAL_REQUEST_BODY),
          eq("INFO"));
      verify(userNotificationStompPublisher).publish(
          any(), eq("APPROVAL_REQUEST"),
          eq(MessageCodes.NOTIFICATION_APPROVAL_REQUEST_TITLE),
          eq(MessageCodes.NOTIFICATION_APPROVAL_REQUEST_BODY),
          eq("INFO"), isNull());
    }
  }

  @Nested
  @DisplayName("onActionCompleted")
  class OnActionCompleted {

    @Test
    @DisplayName("should push STOMP update with SUCCEEDED status")
    void should_push_succeeded() {
      var event = new ActionCompletedEvent(
          42L, 1L, 100L, ActionExecutionMode.LIVE,
          BigDecimal.valueOf(999), ActionReconciliationSource.AUTO);

      listener.onActionCompleted(event);

      verify(stompPublisher).publishActionStatusUpdate(
          1L, 42L, "SUCCEEDED", "LIVE");
    }
  }

  @Nested
  @DisplayName("onActionFailed")
  class OnActionFailed {

    @Test
    @DisplayName("should push STOMP update with FAILED status")
    void should_push_failed() {
      var event = new ActionFailedEvent(
          42L, 1L, 100L, ActionExecutionMode.LIVE,
          BigDecimal.valueOf(999), 3, ErrorClassification.NON_RETRIABLE,
          "Provider error");

      listener.onActionFailed(event);

      verify(stompPublisher).publishActionStatusUpdate(
          1L, 42L, "FAILED", "LIVE");
    }

    @Test
    @DisplayName("should not throw when stomp fails")
    void should_not_throw_when_stomp_fails() {
      var event = new ActionFailedEvent(
          1L, 1L, 1L, ActionExecutionMode.LIVE,
          BigDecimal.ONE, 1, ErrorClassification.RETRIABLE_TRANSIENT, "err");

      doThrow(new RuntimeException("down"))
          .when(stompPublisher).publishActionStatusUpdate(
              1L, 1L, "FAILED", "LIVE");

      assertThatCode(() -> listener.onActionFailed(event))
          .doesNotThrowAnyException();
    }
  }
}
