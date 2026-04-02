package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePricePolicyRequest(
        @NotBlank String name,
        @NotNull PolicyType strategyType,
        @NotNull String strategyParams,
        BigDecimal minMarginPct,
        BigDecimal maxPriceChangePct,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String guardConfig,
        @NotNull ExecutionMode executionMode,
        @Min(1) Integer approvalTimeoutHours,
        @Min(0) @Max(1000) Integer priority
) {
}
