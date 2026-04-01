package io.datapulse.analytics.api;

import io.datapulse.analytics.domain.ProvenanceService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/analytics/provenance", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProvenanceController {

    private final ProvenanceService provenanceService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/entry/{entryId}")
    public ProvenanceEntryResponse getCanonicalEntry(@PathVariable("entryId") long entryId) {
        return provenanceService.getCanonicalEntry(entryId, workspaceContext.getWorkspaceId());
    }

    @GetMapping("/entry/{entryId}/raw")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public ProvenanceRawResponse getRawUrl(@PathVariable("entryId") long entryId) {
        return provenanceService.getRawUrl(entryId, workspaceContext.getWorkspaceId());
    }
}
