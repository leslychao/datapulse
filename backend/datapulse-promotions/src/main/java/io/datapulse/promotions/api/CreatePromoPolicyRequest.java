package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.ParticipationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePromoPolicyRequest(
        @NotBlank String name,
        @NotNull ParticipationMode participationMode,
        @NotNull BigDecimal minMarginPct,
        Integer minStockDaysOfCover,
        BigDecimal maxPromoDiscountPct,
        String autoParticipateCategories,
        String autoDeclineCategories,
        String evaluationConfig
) {
}
