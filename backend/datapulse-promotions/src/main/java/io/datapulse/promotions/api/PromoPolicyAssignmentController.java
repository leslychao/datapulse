package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoPolicyAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/promo/policies/{policyId}/assignments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoPolicyAssignmentController {

    private final PromoPolicyAssignmentService assignmentService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<PromoAssignmentResponse> listAssignments(@PathVariable("policyId") Long policyId) {
        return assignmentService.listAssignments(policyId, workspaceContext.getWorkspaceId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PromoAssignmentResponse createAssignment(@PathVariable("policyId") Long policyId,
                                                    @Valid @RequestBody CreatePromoAssignmentRequest request) {
        return assignmentService.createAssignment(
                policyId, request, workspaceContext.getWorkspaceId());
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void deleteAssignment(@PathVariable("policyId") Long policyId,
                                 @PathVariable("assignmentId") Long assignmentId) {
        assignmentService.deleteAssignment(
                policyId, assignmentId, workspaceContext.getWorkspaceId());
    }
}
