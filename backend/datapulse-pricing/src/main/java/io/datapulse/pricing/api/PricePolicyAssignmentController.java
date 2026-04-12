package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PricePolicyAssignmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/pricing/policies/{policyId}/assignments",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricePolicyAssignmentController {

    private final PricePolicyAssignmentService assignmentService;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<AssignmentResponse> listAssignments(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId) {
        return assignmentService.listAssignments(policyId, workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public AssignmentResponse createAssignment(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId,
            @Valid @RequestBody CreateAssignmentRequest request) {
        return assignmentService.createAssignment(policyId, request, workspaceId);
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void deleteAssignment(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId,
            @PathVariable("assignmentId") Long assignmentId) {
        assignmentService.deleteAssignment(policyId, assignmentId, workspaceId);
    }

    @GetMapping("/categories")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<CategorySuggestionResponse> listCategories(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId,
            @RequestParam("sourcePlatform") String sourcePlatform,
            @RequestParam(value = "search", required = false) String search) {
        return assignmentService.listCategories(workspaceId, sourcePlatform, search);
    }

    @GetMapping("/offers")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<OfferSuggestionResponse> searchOffers(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId,
            @RequestParam("sourcePlatform") String sourcePlatform,
            @RequestParam(value = "search", required = false) String search) {
        return assignmentService.searchOffers(workspaceId, sourcePlatform, search);
    }
}
