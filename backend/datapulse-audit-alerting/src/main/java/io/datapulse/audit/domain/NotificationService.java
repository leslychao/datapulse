package io.datapulse.audit.domain;

import java.util.List;
import java.util.Map;

import io.datapulse.audit.api.NotificationResponse;
import io.datapulse.audit.persistence.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationRepository notificationRepository;

    private static final Map<String, List<String>> ROLE_FILTER = Map.of(
            "CRITICAL", List.of("OWNER", "ADMIN", "PRICING_MANAGER", "OPERATOR"),
            "WARNING", List.of("OWNER", "ADMIN", "PRICING_MANAGER", "OPERATOR"),
            "INFO", List.of("OWNER", "ADMIN", "PRICING_MANAGER", "OPERATOR", "ANALYST")
    );

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(long userId, long workspaceId, Pageable pageable) {
        List<NotificationResponse> content = notificationRepository.findByUserAndWorkspace(
                userId, workspaceId, pageable.getPageSize(), pageable.getOffset());
        long total = notificationRepository.countByUserAndWorkspace(userId, workspaceId);
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public long unreadCount(long userId, long workspaceId) {
        return notificationRepository.countUnread(userId, workspaceId);
    }

    @Transactional
    public void markRead(long id, long userId, long workspaceId) {
        notificationRepository.markRead(id, userId, workspaceId);
    }

    @Transactional
    public void markAllRead(long userId, long workspaceId) {
        notificationRepository.markAllRead(userId, workspaceId);
    }

    /**
     * Creates a notification for each workspace member with sufficient role.
     * Returns list of (userId, notificationId) pairs for WebSocket push.
     */
    @Transactional
    public List<long[]> fanOut(long workspaceId, Long alertEventId,
                               String notificationType, String title, String body,
                               String severity) {
        List<String> roles = ROLE_FILTER.getOrDefault(severity,
                List.of("OWNER", "ADMIN", "PRICING_MANAGER", "OPERATOR"));

        List<Long> userIds = notificationRepository.findMemberUserIds(workspaceId, roles);

        return userIds.stream()
                .map(userId -> {
                    long notifId = notificationRepository.insert(
                            workspaceId, userId, alertEventId,
                            notificationType, title, body, severity);
                    return new long[]{userId, notifId};
                })
                .toList();
    }
}
