package io.datapulse.analytics.persistence;

import java.math.BigDecimal;

public record CampaignPgRow(
    long id,
    long connectionId,
    String externalCampaignId,
    String name,
    String campaignType,
    String status,
    String placement,
    BigDecimal dailyBudget,
    String sourcePlatform
) {}
