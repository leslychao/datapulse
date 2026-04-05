package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record CampaignSummaryResponse(
    long id,
    String externalCampaignId,
    String name,
    String sourcePlatform,
    String campaignType,
    String status,
    BigDecimal dailyBudget,
    BigDecimal spendForPeriod,
    Integer ordersForPeriod,
    BigDecimal drrPct,
    String drrTrend
) {}
