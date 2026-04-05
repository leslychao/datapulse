package io.datapulse.analytics.persistence;

import java.math.BigDecimal;

public record CampaignMetrics(
    long connectionId,
    long campaignId,
    BigDecimal currentSpend,
    int currentOrders,
    BigDecimal currentRevenue,
    BigDecimal prevSpend,
    BigDecimal prevRevenue
) {}
