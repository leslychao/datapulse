package io.datapulse.pricing.api;

import java.util.List;

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
import org.springframework.web.multipart.MultipartFile;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.pricing.domain.CompetitorService;
import io.datapulse.pricing.persistence.CompetitorMatchEntity;
import io.datapulse.pricing.persistence.CompetitorObservationEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/competitors",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CompetitorController {

    private final CompetitorService competitorService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/matches")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<CompetitorMatchResponse> listMatches(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "marketplaceOfferId", required = false)
            Long marketplaceOfferId) {
        return competitorService.listMatches(workspaceId, marketplaceOfferId).stream()
                .map(this::toMatchResponse)
                .toList();
    }

    @PostMapping("/matches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public CompetitorMatchResponse createMatch(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody CreateCompetitorMatchRequest request) {
        CompetitorMatchEntity entity = competitorService.createMatch(
                workspaceId, request.marketplaceOfferId(),
                request.competitorName(), request.competitorListingUrl(),
                workspaceContext.getUserId());
        return toMatchResponse(entity);
    }

    @DeleteMapping("/matches/{matchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void deleteMatch(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("matchId") long matchId) {
        competitorService.deleteMatch(matchId, workspaceId);
    }

    @PostMapping("/matches/{matchId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public CompetitorObservationResponse addObservation(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("matchId") long matchId,
            @Valid @RequestBody CreateCompetitorObservationRequest request) {
        CompetitorObservationEntity entity = competitorService.addObservation(
                matchId, workspaceId,
                request.competitorPrice(), request.observedAt());
        return toObservationResponse(entity);
    }

    @GetMapping("/matches/{matchId}/observations")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<CompetitorObservationResponse> listObservations(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("matchId") long matchId) {
        return competitorService.listObservations(matchId, workspaceId).stream()
                .map(this::toObservationResponse)
                .toList();
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public BulkCompetitorUploadResponse bulkUpload(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam("file") MultipartFile file) {
        try {
            CompetitorService.BulkUploadResult result =
                    competitorService.bulkUploadCsv(workspaceId,
                            workspaceContext.getUserId(), file.getInputStream());
            return new BulkCompetitorUploadResponse(
                    result.totalRows(), result.created(),
                    result.skipped(), result.errors());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    private CompetitorMatchResponse toMatchResponse(CompetitorMatchEntity e) {
        return new CompetitorMatchResponse(
                e.getId(), e.getWorkspaceId(), e.getMarketplaceOfferId(),
                e.getCompetitorName(), e.getCompetitorListingUrl(),
                e.getMatchMethod(), e.getTrustLevel(), e.getMatchedBy(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private CompetitorObservationResponse toObservationResponse(
            CompetitorObservationEntity e) {
        return new CompetitorObservationResponse(
                e.getId(), e.getCompetitorMatchId(),
                e.getCompetitorPrice(), e.getCurrency(),
                e.getObservedAt(), e.getCreatedAt());
    }
}
