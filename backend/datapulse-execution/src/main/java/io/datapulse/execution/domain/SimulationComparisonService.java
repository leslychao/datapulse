package io.datapulse.execution.domain;

import io.datapulse.execution.persistence.SimulationComparisonRepository;
import io.datapulse.execution.persistence.SimulationComparisonRepository.CoverageRow;
import io.datapulse.execution.persistence.SimulationComparisonRepository.SimulationComparisonRow;
import io.datapulse.execution.persistence.SimulationComparisonRepository.SimulationSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Builds a comparison report: what prices would have been set (shadow-state)
 * vs current real prices from canonical_price_current.
 *
 * Report is built on-demand per docs: lightweight query,
 * not a materialized view.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationComparisonService {

    private final SimulationComparisonRepository comparisonRepository;

    public SimulationComparisonReport buildReport(long workspaceId, long connectionId) {
        SimulationSummaryRow summary = comparisonRepository.findSummary(workspaceId, connectionId);
        CoverageRow coverage = comparisonRepository.findCoverage(workspaceId, connectionId);
        List<SimulationComparisonRow> items = comparisonRepository.findComparisonItems(
                workspaceId, connectionId);

        BigDecimal coveragePct = BigDecimal.ZERO;
        if (coverage.totalOffers() > 0) {
            coveragePct = BigDecimal.valueOf(coverage.simulatedCount())
                    .divide(BigDecimal.valueOf(coverage.totalOffers()), 4, RoundingMode.HALF_UP);
        }

        return new SimulationComparisonReport(
                connectionId,
                summary.totalSimulated(),
                summary.avgDeltaPct(),
                summary.countIncrease(),
                summary.countDecrease(),
                summary.countUnchanged(),
                summary.totalDeltaSum(),
                coverage.simulatedCount(),
                coverage.totalOffers(),
                coveragePct,
                items
        );
    }

    public List<SimulationComparisonRow> previewByDecision(long workspaceId, long decisionId) {
        return comparisonRepository.findByDecision(workspaceId, decisionId);
    }

    public record SimulationComparisonReport(
            long connectionId,
            long totalSimulatedActions,
            BigDecimal avgDeltaPct,
            long countIncrease,
            long countDecrease,
            long countUnchanged,
            BigDecimal totalDeltaSum,
            long simulatedOfferCount,
            long totalOfferCount,
            BigDecimal coveragePct,
            List<SimulationComparisonRow> items
    ) {
    }
}
