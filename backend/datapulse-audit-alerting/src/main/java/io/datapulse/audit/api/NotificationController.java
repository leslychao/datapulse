package io.datapulse.audit.api;

import io.datapulse.audit.domain.NotificationService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<NotificationResponse> listNotifications(Pageable pageable) {
        return notificationService.listNotifications(
                workspaceContext.getUserId(), workspaceContext.getWorkspaceId(), pageable);
    }

    @GetMapping("/unread-count")
    public long unreadCount() {
        return notificationService.unreadCount(
                workspaceContext.getUserId(), workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable("id") long id) {
        notificationService.markRead(
                id, workspaceContext.getUserId(), workspaceContext.getWorkspaceId());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notificationService.markAllRead(
                workspaceContext.getUserId(), workspaceContext.getWorkspaceId());
    }
}
