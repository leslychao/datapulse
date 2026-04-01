package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoDecisionService;
import io.datapulse.promotions.domain.PromoDecisionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/promo/decisions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoDecisionController {

    private final PromoDecisionService decisionService;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PromoDecisionResponse> listDecisions(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "decisionType", required = false) PromoDecisionType decisionType,
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            Pageable pageable) {
        return decisionService.listDecisions(
                workspaceId, decisionType, campaignId, from, to, pageable);
    }
}
