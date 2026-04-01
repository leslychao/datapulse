package io.datapulse.sellerops.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.sellerops.domain.SavedViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/views",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SavedViewController {

    private final SavedViewService savedViewService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<SavedViewSummaryResponse> listViews(
            @PathVariable("workspaceId") Long workspaceId) {
        return savedViewService.listViews(workspaceId, workspaceContext.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public SavedViewSummaryResponse createView(
            @PathVariable("workspaceId") Long workspaceId,
            @Valid @RequestBody CreateSavedViewRequest request) {
        return savedViewService.createView(workspaceId, workspaceContext.getUserId(), request);
    }

    @PutMapping("/{viewId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public SavedViewSummaryResponse updateView(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("viewId") Long viewId,
            @Valid @RequestBody UpdateSavedViewRequest request) {
        return savedViewService.updateView(
                workspaceId, workspaceContext.getUserId(), viewId, request);
    }

    @DeleteMapping("/{viewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public void deleteView(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("viewId") Long viewId) {
        savedViewService.deleteView(workspaceId, workspaceContext.getUserId(), viewId);
    }
}
