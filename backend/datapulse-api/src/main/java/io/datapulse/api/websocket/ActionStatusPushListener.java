package io.datapulse.api.websocket;

import io.datapulse.audit.domain.NotificationService;
import io.datapulse.audit.domain.NotificationType;
import io.datapulse.audit.domain.UserNotificationStompPublisher;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.event.ActionCompletedEvent;
import io.datapulse.execution.domain.event.ActionCreatedEvent;
import io.datapulse.execution.domain.event.ActionFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Pushes action status changes to workspace STOMP subscribers and creates
 * {@code APPROVAL_REQUEST} notifications when an action needs manual approval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionStatusPushListener {

  private final WorkspaceTopicStompPublisher stompPublisher;
  private final NotificationService notificationService;
  private final UserNotificationStompPublisher userNotificationStompPublisher;

  @Async("notificationExecutor")
  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onActionCreated(ActionCreatedEvent event) {
    try {
      stompPublisher.publishActionStatusUpdate(
          event.workspaceId(), event.actionId(),
          event.initialStatus().name(), event.executionMode().name());

      if (event.initialStatus() == ActionStatus.PENDING_APPROVAL) {
        sendApprovalRequestNotification(event);
      }
    } catch (Exception e) {
      log.error("Failed to push action created: actionId={}, error={}",
          event.actionId(), e.getMessage(), e);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onActionCompleted(ActionCompletedEvent event) {
    try {
      stompPublisher.publishActionStatusUpdate(
          event.workspaceId(), event.actionId(),
          "SUCCEEDED", event.executionMode().name());
    } catch (Exception e) {
      log.error("Failed to push action completed: actionId={}, error={}",
          event.actionId(), e.getMessage(), e);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onActionFailed(ActionFailedEvent event) {
    try {
      stompPublisher.publishActionStatusUpdate(
          event.workspaceId(), event.actionId(),
          "FAILED", event.executionMode().name());
    } catch (Exception e) {
      log.error("Failed to push action failed: actionId={}, error={}",
          event.actionId(), e.getMessage(), e);
    }
  }

  private void sendApprovalRequestNotification(ActionCreatedEvent event) {
    List<long[]> notifications = notificationService.fanOut(
        event.workspaceId(),
        null,
        NotificationType.APPROVAL_REQUEST.name(),
        MessageCodes.NOTIFICATION_APPROVAL_REQUEST_TITLE,
        MessageCodes.NOTIFICATION_APPROVAL_REQUEST_BODY,
        "INFO");

    userNotificationStompPublisher.publish(
        notifications,
        NotificationType.APPROVAL_REQUEST.name(),
        MessageCodes.NOTIFICATION_APPROVAL_REQUEST_TITLE,
        MessageCodes.NOTIFICATION_APPROVAL_REQUEST_BODY,
        "INFO",
        null);

    log.debug("Approval request notifications sent: actionId={}, recipients={}",
        event.actionId(), notifications.size());
  }
}
