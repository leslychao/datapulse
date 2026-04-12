package io.datapulse.execution.api;

import java.math.BigDecimal;
import java.util.List;

public record SimulationComparisonResponse(
        String sourcePlatform,
        SimulationSummary summary,
        List<SimulationComparisonItemResponse> items
) {

    public record SimulationSummary(
            long totalSimulatedActions,
            BigDecimal avgDeltaPct,
            long countIncrease,
            long countDecrease,
            long countUnchanged,
            BigDecimal totalDeltaSum,
            long simulatedOfferCount,
            long totalOfferCount,
            BigDecimal coveragePct
    ) {
    }
}
