package io.datapulse.sellerops.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.sellerops.config.QueueProperties;
import io.datapulse.sellerops.domain.WorkingQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/queues",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class WorkingQueueController {

    private final WorkingQueueService queueService;
    private final WorkspaceContext workspaceContext;
    private final QueueProperties queueProperties;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<QueueSummaryResponse> listQueues(
            @PathVariable("workspaceId") Long workspaceId) {
        return queueService.listQueues(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public QueueSummaryResponse createQueue(
            @PathVariable("workspaceId") Long workspaceId,
            @Valid @RequestBody CreateQueueRequest request) {
        return queueService.createQueue(workspaceId, request);
    }

    @GetMapping("/{queueId}/items")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<QueueItemResponse> listItems(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("queueId") Long queueId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int clampedSize = Math.min(size, queueProperties.getMaxPageSize());
        return queueService.listItems(workspaceId, queueId, status,
                PageRequest.of(page, clampedSize));
    }

    @PostMapping("/{queueId}/items/{itemId}/claim")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public QueueItemResponse claimItem(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("queueId") Long queueId,
            @PathVariable("itemId") Long itemId) {
        return queueService.claimItem(workspaceId, queueId, itemId,
                workspaceContext.getUserId());
    }

    @PostMapping("/{queueId}/items/{itemId}/done")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public QueueItemResponse markDone(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("queueId") Long queueId,
            @PathVariable("itemId") Long itemId,
            @RequestBody(required = false) QueueItemActionRequest request) {
        return queueService.markDone(workspaceId, queueId, itemId,
                request != null ? request.note() : null);
    }

    @PostMapping("/{queueId}/items/{itemId}/dismiss")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public QueueItemResponse dismissItem(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("queueId") Long queueId,
            @PathVariable("itemId") Long itemId,
            @RequestBody(required = false) QueueItemActionRequest request) {
        return queueService.dismissItem(workspaceId, queueId, itemId,
                request != null ? request.note() : null);
    }
}
