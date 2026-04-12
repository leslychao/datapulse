package io.datapulse.api.websocket;

import java.util.List;
import java.util.Map;

import io.datapulse.audit.domain.NotificationService;
import io.datapulse.audit.domain.NotificationType;
import io.datapulse.audit.domain.UserNotificationStompPublisher;
import io.datapulse.bidding.domain.BiddingRunStatus;
import io.datapulse.bidding.domain.event.BiddingRunCompletedEvent;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes bidding run completion/pause events to workspace STOMP subscribers
 * and creates user notifications for paused runs (blast radius breached).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiddingRunStatusPushListener {

  private final WorkspaceTopicStompPublisher stompPublisher;
  private final NotificationService notificationService;
  private final UserNotificationStompPublisher userNotificationStompPublisher;

  @Async("notificationExecutor")
  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBiddingRunCompleted(BiddingRunCompletedEvent event) {
    try {
      String type = BiddingRunStatus.PAUSED.name().equals(event.status())
          ? "BIDDING_RUN_PAUSED"
          : "BIDDING_RUN_COMPLETED";

      Map<String, Object> payload = Map.of(
          "type", type,
          "workspaceId", event.workspaceId(),
          "runId", event.runId(),
          "stats", Map.of(
              "bidUp", event.totalBidUp(),
              "bidDown", event.totalBidDown(),
              "hold", event.totalHold(),
              "paused", event.totalPause()));

      stompPublisher.publishBiddingRunUpdate(event.workspaceId(), payload);

      if (BiddingRunStatus.PAUSED.name().equals(event.status())) {
        sendPausedNotification(event);
      }
    } catch (Exception e) {
      log.error("Failed to push bidding run status: runId={}, error={}",
          event.runId(), e.getMessage(), e);
    }
  }

  private void sendPausedNotification(BiddingRunCompletedEvent event) {
    List<long[]> notifications = notificationService.fanOut(
        event.workspaceId(),
        null,
        NotificationType.ALERT.name(),
        MessageCodes.BIDDING_RUN_PAUSED,
        MessageCodes.BIDDING_RUN_PAUSED,
        "WARNING");

    userNotificationStompPublisher.publish(
        notifications,
        NotificationType.ALERT.name(),
        MessageCodes.BIDDING_RUN_PAUSED,
        MessageCodes.BIDDING_RUN_PAUSED,
        "WARNING",
        null);
  }
}
