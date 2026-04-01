package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PromoJournalEntryResponse(
        long decisionId,
        OffsetDateTime decisionDate,
        String promoName,
        String promoType,
        LocalDate periodFrom,
        LocalDate periodTo,
        String evaluationResult,
        String participationDecision,
        String actionStatus,
        BigDecimal requiredPrice,
        BigDecimal marginAtPromoPrice,
        BigDecimal marginDeltaPct,
        String explanationSummary
) {
}
