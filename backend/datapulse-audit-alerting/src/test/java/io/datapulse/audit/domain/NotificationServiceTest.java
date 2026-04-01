package io.datapulse.audit.domain;

import io.datapulse.audit.api.NotificationResponse;
import io.datapulse.audit.persistence.UserNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock
  private UserNotificationRepository notificationRepository;

  @InjectMocks
  private NotificationService notificationService;

  @Nested
  @DisplayName("fanOut")
  class FanOut {

    @Test
    @DisplayName("should_create_notification_for_each_eligible_member")
    void should_create_notification_for_each_eligible_member() {
      when(notificationRepository.findMemberUserIds(eq(1L), anyList()))
          .thenReturn(List.of(10L, 20L, 30L));
      when(notificationRepository.insert(
          anyLong(), anyLong(), any(), anyString(), anyString(), any(), anyString()))
          .thenReturn(100L, 101L, 102L);

      List<long[]> result = notificationService.fanOut(
          1L, 42L, "ALERT", "Test alert", null, "CRITICAL");

      assertThat(result).hasSize(3);
      assertThat(result.get(0)[0]).isEqualTo(10L);
      assertThat(result.get(0)[1]).isEqualTo(100L);
    }

    @Test
    @DisplayName("should_use_role_filter_for_info_severity_including_analyst")
    void should_use_role_filter_for_info_severity_including_analyst() {
      when(notificationRepository.findMemberUserIds(eq(1L), anyList()))
          .thenReturn(List.of());

      notificationService.fanOut(1L, 42L, "ALERT", "Info", null, "INFO");

      verify(notificationRepository).findMemberUserIds(1L,
          List.of("OWNER", "ADMIN", "PRICING_MANAGER", "OPERATOR", "ANALYST"));
    }

    @Test
    @DisplayName("should_use_default_roles_for_unknown_severity")
    void should_use_default_roles_for_unknown_severity() {
      when(notificationRepository.findMemberUserIds(eq(1L), anyList()))
          .thenReturn(List.of());

      notificationService.fanOut(1L, 42L, "ALERT", "Unknown", null, "UNKNOWN");

      verify(notificationRepository).findMemberUserIds(1L,
          List.of("OWNER", "ADMIN", "PRICING_MANAGER", "OPERATOR"));
    }

    @Test
    @DisplayName("should_return_empty_list_when_no_eligible_members")
    void should_return_empty_list_when_no_eligible_members() {
      when(notificationRepository.findMemberUserIds(eq(1L), anyList()))
          .thenReturn(List.of());

      List<long[]> result = notificationService.fanOut(
          1L, 42L, "ALERT", "Test", null, "WARNING");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("listNotifications")
  class ListNotifications {

    @Test
    @DisplayName("should_return_paginated_notifications")
    void should_return_paginated_notifications() {
      var notif = new NotificationResponse(
          1L, 1L, 10L, 42L, "ALERT",
          "Test", null, "WARNING", null, OffsetDateTime.now());

      when(notificationRepository.findByUserAndWorkspace(10L, 1L, 10, 0L))
          .thenReturn(List.of(notif));
      when(notificationRepository.countByUserAndWorkspace(10L, 1L))
          .thenReturn(1L);

      Page<NotificationResponse> result = notificationService.listNotifications(
          10L, 1L, PageRequest.of(0, 10));

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getTotalElements()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("unreadCount")
  class UnreadCount {

    @Test
    @DisplayName("should_return_count_from_repository")
    void should_return_count_from_repository() {
      when(notificationRepository.countUnread(10L, 1L)).thenReturn(5L);

      long count = notificationService.unreadCount(10L, 1L);

      assertThat(count).isEqualTo(5L);
    }
  }

  @Nested
  @DisplayName("markRead")
  class MarkRead {

    @Test
    @DisplayName("should_delegate_to_repository")
    void should_delegate_to_repository() {
      notificationService.markRead(1L, 10L, 1L);

      verify(notificationRepository).markRead(1L, 10L, 1L);
    }
  }

  @Nested
  @DisplayName("markAllRead")
  class MarkAllRead {

    @Test
    @DisplayName("should_delegate_to_repository")
    void should_delegate_to_repository() {
      notificationService.markAllRead(10L, 1L);

      verify(notificationRepository).markAllRead(10L, 1L);
    }
  }
}
