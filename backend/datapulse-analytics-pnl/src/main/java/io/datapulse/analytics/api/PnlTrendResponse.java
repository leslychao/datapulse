package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record PnlTrendResponse(
        String period,
        BigDecimal revenueAmount,
        BigDecimal totalCostsAmount,
        BigDecimal cogsAmount,
        BigDecimal advertisingCostAmount,
        BigDecimal fullPnl
) {}
