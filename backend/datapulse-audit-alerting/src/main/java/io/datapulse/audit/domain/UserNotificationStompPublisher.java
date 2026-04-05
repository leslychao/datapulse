package io.datapulse.audit.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Single place for STOMP push to {@code /user/queue/notifications} after {@link NotificationService#fanOut}.
 */
@Component
@RequiredArgsConstructor
public class UserNotificationStompPublisher {

  private static final String USER_QUEUE_DESTINATION = "/queue/notifications";

  private final SimpMessagingTemplate messagingTemplate;

  /**
   * Sends one WebSocket message per (userId, notificationId) pair. Payload matches the contract expected
   * by the frontend ({@code id}, {@code notificationId}, {@code notificationType}, {@code alertEventId},
   * {@code severity}, {@code title}, {@code body}, {@code createdAt}, {@code read}).
   */
  public void publish(
      List<long[]> userIdNotificationIdPairs,
      String notificationType,
      String title,
      String body,
      String severity,
      Long alertEventId) {
    if (CollectionUtils.isEmpty(userIdNotificationIdPairs)) {
      return;
    }
    String createdAt = OffsetDateTime.now().toString();
    for (long[] pair : userIdNotificationIdPairs) {
      if (pair == null || pair.length < 2) {
        continue;
      }
      long userId = pair[0];
      long notificationId = pair[1];
      messagingTemplate.convertAndSendToUser(
          String.valueOf(userId),
          USER_QUEUE_DESTINATION,
          buildPayload(
              notificationId, notificationType, title, body, severity, alertEventId, createdAt));
    }
  }

  private static Map<String, Object> buildPayload(
      long notificationId,
      String notificationType,
      String title,
      String body,
      String severity,
      Long alertEventId,
      String createdAt) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", notificationId);
    map.put("notificationId", notificationId);
    map.put("notificationType", notificationType);
    map.put("alertEventId", alertEventId);
    map.put("severity", severity);
    map.put("title", title);
    map.put("body", body);
    map.put("createdAt", createdAt);
    map.put("read", false);
    return map;
  }
}
