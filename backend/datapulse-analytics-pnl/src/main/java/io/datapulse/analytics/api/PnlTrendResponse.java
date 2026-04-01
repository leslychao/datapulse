package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record PnlTrendResponse(
        String periodLabel,
        BigDecimal revenueAmount,
        BigDecimal totalCosts,
        BigDecimal refundAmount,
        BigDecimal compensationAmount,
        BigDecimal advertisingCost,
        BigDecimal netCogs,
        BigDecimal marketplacePnl,
        BigDecimal fullPnl
) {}
