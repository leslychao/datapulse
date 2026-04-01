package io.datapulse.execution.api;

import io.datapulse.execution.domain.SimulationComparisonService;
import io.datapulse.execution.domain.SimulationComparisonService.SimulationComparisonReport;
import io.datapulse.execution.domain.SimulationService;
import io.datapulse.execution.persistence.SimulationComparisonRepository.SimulationComparisonRow;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/simulation", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationComparisonService comparisonService;
    private final SimulationService simulationService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/comparison")
    public SimulationComparisonResponse comparison(@RequestParam("connectionId") long connectionId) {
        long workspaceId = workspaceContext.getWorkspaceId();
        SimulationComparisonReport report = comparisonService.buildReport(workspaceId, connectionId);
        return toResponse(report);
    }

    @GetMapping("/preview")
    public List<SimulationComparisonItemResponse> preview(@RequestParam("decisionId") long decisionId) {
        long workspaceId = workspaceContext.getWorkspaceId();
        return comparisonService.previewByDecision(workspaceId, decisionId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    @DeleteMapping("/shadow-state")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void resetShadowState(@Valid @RequestBody ResetShadowStateRequest request) {
        long workspaceId = workspaceContext.getWorkspaceId();
        simulationService.resetShadowState(workspaceId, request.connectionId());
    }

    private SimulationComparisonResponse toResponse(SimulationComparisonReport report) {
        var summary = new SimulationComparisonResponse.SimulationSummary(
                report.totalSimulatedActions(),
                report.avgDeltaPct(),
                report.countIncrease(),
                report.countDecrease(),
                report.countUnchanged(),
                report.totalDeltaSum(),
                report.simulatedOfferCount(),
                report.totalOfferCount(),
                report.coveragePct()
        );

        List<SimulationComparisonItemResponse> items = report.items().stream()
                .map(this::toItemResponse)
                .toList();

        return new SimulationComparisonResponse(report.connectionId(), summary, items);
    }

    private SimulationComparisonItemResponse toItemResponse(SimulationComparisonRow row) {
        return new SimulationComparisonItemResponse(
                row.marketplaceOfferId(),
                row.marketplaceSku(),
                row.simulatedPrice(),
                row.canonicalPriceAtSimulation(),
                row.currentRealPrice(),
                row.priceDelta(),
                row.priceDeltaPct(),
                row.previousSimulatedPrice(),
                row.simulatedAt(),
                row.priceActionId()
        );
    }
}
