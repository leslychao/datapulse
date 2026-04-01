package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record ReconciliationResponse(
        long connectionId,
        String sourcePlatform,
        int period,
        BigDecimal totalNetPayout,
        BigDecimal totalMeasuresSum,
        BigDecimal totalResidual,
        BigDecimal residualRatio,
        BigDecimal baselineResidualRatio,
        boolean anomaly
) {}
