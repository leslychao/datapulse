package io.datapulse.audit.api;

import java.util.List;

import io.datapulse.audit.domain.AlertRuleService;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/alert-rules", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<AlertRuleResponse> listRules() {
        return alertRuleService.listRules(workspaceContext.getWorkspaceId());
    }

    @GetMapping("/{id}")
    public AlertRuleResponse getRule(@PathVariable("id") long id) {
        return alertRuleService.getRule(id, workspaceContext.getWorkspaceId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public AlertRuleResponse updateRule(@PathVariable("id") long id,
                                        @Valid @RequestBody UpdateAlertRuleRequest request) {
        return alertRuleService.updateRule(id, workspaceContext.getWorkspaceId(), request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public AlertRuleResponse activate(@PathVariable("id") long id) {
        return alertRuleService.activate(id, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public AlertRuleResponse deactivate(@PathVariable("id") long id) {
        return alertRuleService.deactivate(id, workspaceContext.getWorkspaceId());
    }
}
