package io.datapulse.audit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserNotificationStompPublisherTest {

  @Mock
  private SimpMessagingTemplate messagingTemplate;

  @InjectMocks
  private UserNotificationStompPublisher publisher;

  @Nested
  @DisplayName("publish")
  class Publish {

    @Test
    @DisplayName("should_send_full_payload_per_user")
    void should_send_full_payload_per_user() {
      publisher.publish(
          List.of(new long[]{10L, 100L}),
          "SYNC_COMPLETED",
          "title.key",
          "body.key",
          "INFO",
          null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
      verify(messagingTemplate)
          .convertAndSendToUser(eq("10"), eq("/queue/notifications"), captor.capture());

      Map<String, Object> payload = captor.getValue();
      assertThat(payload.get("id")).isEqualTo(100L);
      assertThat(payload.get("notificationId")).isEqualTo(100L);
      assertThat(payload.get("notificationType")).isEqualTo("SYNC_COMPLETED");
      assertThat(payload.get("alertEventId")).isNull();
      assertThat(payload.get("severity")).isEqualTo("INFO");
      assertThat(payload.get("title")).isEqualTo("title.key");
      assertThat(payload.get("body")).isEqualTo("body.key");
      assertThat(payload.get("createdAt")).isNotNull();
      assertThat(payload.get("read")).isEqualTo(false);
    }

    @Test
    @DisplayName("should_include_alert_event_id_when_present")
    void should_include_alert_event_id_when_present() {
      publisher.publish(
          List.of(new long[]{5L, 200L}),
          "ALERT",
          "Alert title",
          null,
          "WARNING",
          99L);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
      verify(messagingTemplate)
          .convertAndSendToUser(eq("5"), eq("/queue/notifications"), captor.capture());

      assertThat(captor.getValue().get("alertEventId")).isEqualTo(99L);
      assertThat(captor.getValue().get("body")).isNull();
    }

    @Test
    @DisplayName("should_skip_when_pairs_empty_or_null")
    void should_skip_when_pairs_empty_or_null() {
      publisher.publish(null, "ALERT", "t", null, "INFO", null);
      publisher.publish(List.of(), "ALERT", "t", null, "INFO", null);

      verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("should_skip_malformed_pair_entries")
    void should_skip_malformed_pair_entries() {
      List<long[]> pairs = new ArrayList<>();
      pairs.add(null);
      pairs.add(new long[]{10L});
      pairs.add(new long[]{20L, 300L});
      publisher.publish(pairs, "ALERT", "t", null, "INFO", 1L);

      verify(messagingTemplate, times(1))
          .convertAndSendToUser(eq("20"), eq("/queue/notifications"), any());
    }
  }
}
