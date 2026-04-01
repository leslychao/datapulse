package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoDecisionService;
import io.datapulse.promotions.domain.PromoDecisionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/promo/decisions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoDecisionController {

    private final PromoDecisionService decisionService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<PromoDecisionResponse> listDecisions(
            @RequestParam(value = "decisionType", required = false) PromoDecisionType decisionType,
            Pageable pageable) {
        return decisionService.listDecisions(
                workspaceContext.getWorkspaceId(), decisionType, pageable);
    }
}
