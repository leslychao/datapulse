package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PricePolicyAssignmentService;
import io.datapulse.platform.security.WorkspaceContext;
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
@RequestMapping(value = "/api/pricing/policies/{policyId}/assignments",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricePolicyAssignmentController {

    private final PricePolicyAssignmentService assignmentService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<AssignmentResponse> listAssignments(@PathVariable("policyId") Long policyId) {
        return assignmentService.listAssignments(policyId, workspaceContext.getWorkspaceId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public AssignmentResponse createAssignment(@PathVariable("policyId") Long policyId,
                                               @Valid @RequestBody CreateAssignmentRequest request) {
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
