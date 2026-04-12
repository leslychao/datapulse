package io.datapulse.bidding.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceBiddingSettingsRequest(
    @NotNull Boolean biddingEnabled,
    @DecimalMin("0") BigDecimal maxAggregateDailySpend,
    @NotNull @Min(1) @Max(168) Integer minDecisionIntervalHours
) {}
