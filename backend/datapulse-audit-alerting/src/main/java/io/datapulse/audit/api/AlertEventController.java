package io.datapulse.audit.api;

import io.datapulse.audit.domain.AlertEventService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AlertEventController {

    private final AlertEventService alertEventService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/summary")
    public AlertSummaryResponse getSummary() {
        return alertEventService.getSummary(workspaceContext.getWorkspaceId());
    }

    @GetMapping
    public Page<AlertEventResponse> listAlerts(AlertEventFilter filter, Pageable pageable) {
        return alertEventService.listAlerts(workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @PostMapping("/{id}/acknowledge")
    public AlertEventResponse acknowledge(@PathVariable("id") long id) {
        return alertEventService.acknowledge(
                id, workspaceContext.getWorkspaceId(), workspaceContext.getUserId());
    }

    @PostMapping("/{id}/resolve")
    public AlertEventResponse resolve(@PathVariable("id") long id) {
        return alertEventService.resolve(id, workspaceContext.getWorkspaceId());
    }
}
