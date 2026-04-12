package io.datapulse.bidding.api;

import java.math.BigDecimal;

public record WorkspaceBiddingSettingsResponse(
    boolean biddingEnabled,
    BigDecimal maxAggregateDailySpend,
    int minDecisionIntervalHours
) {}
