package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PriceDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/pricing/decisions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PriceDecisionController {

    private final PriceDecisionService decisionService;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PriceDecisionResponse> listDecisions(
            @PathVariable("workspaceId") long workspaceId,
            PriceDecisionFilter filter,
            Pageable pageable) {
        return decisionService.listDecisions(workspaceId, filter, pageable);
    }

    @GetMapping("/{decisionId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PriceDecisionResponse getDecision(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("decisionId") Long decisionId) {
        return decisionService.getDecision(decisionId, workspaceId);
    }
}
