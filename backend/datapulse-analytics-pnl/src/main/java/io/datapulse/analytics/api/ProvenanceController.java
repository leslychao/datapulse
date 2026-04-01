package io.datapulse.analytics.api;

import io.datapulse.analytics.domain.ProvenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/analytics/provenance",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProvenanceController {

    private final ProvenanceService provenanceService;

    @GetMapping("/entry/{entryId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public ProvenanceEntryResponse getCanonicalEntry(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("entryId") long entryId) {
        return provenanceService.getCanonicalEntry(entryId, workspaceId);
    }

    @GetMapping("/entry/{entryId}/raw")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public ProvenanceRawResponse getRawUrl(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("entryId") long entryId) {
        return provenanceService.getRawUrl(entryId, workspaceId);
    }
}
