package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PriceDecisionService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/pricing/decisions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PriceDecisionController {

    private final PriceDecisionService decisionService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<PriceDecisionResponse> listDecisions(PriceDecisionFilter filter, Pageable pageable) {
        return decisionService.listDecisions(workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @GetMapping("/{decisionId}")
    public PriceDecisionResponse getDecision(@PathVariable("decisionId") Long decisionId) {
        return decisionService.getDecision(decisionId, workspaceContext.getWorkspaceId());
    }
}
