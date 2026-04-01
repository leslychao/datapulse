package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/promo/products",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoProductController {

    private final PromoDecisionService decisionService;
    private final WorkspaceContext workspaceContext;

    @PostMapping("/{promoProductId}/participate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void participate(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("promoProductId") Long promoProductId,
            @Valid @RequestBody(required = false) ManualParticipateRequest request) {
        decisionService.manualParticipate(
                promoProductId,
                request != null ? request.targetPromoPrice() : null,
                workspaceId,
                workspaceContext.getUserId());
    }

    @PostMapping("/{promoProductId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void decline(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("promoProductId") Long promoProductId,
            @Valid @RequestBody(required = false) ManualDeclineRequest request) {
        decisionService.manualDecline(
                promoProductId,
                request != null ? request.reason() : null,
                workspaceId,
                workspaceContext.getUserId());
    }

    @PostMapping("/{promoProductId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void deactivate(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("promoProductId") Long promoProductId,
            @Valid @RequestBody(required = false) ManualDeclineRequest request) {
        decisionService.manualDeactivate(
                promoProductId,
                request != null ? request.reason() : null,
                workspaceId,
                workspaceContext.getUserId());
    }
}
