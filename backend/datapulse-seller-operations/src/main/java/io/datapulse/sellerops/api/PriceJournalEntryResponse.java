package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceJournalEntryResponse(
        long decisionId,
        OffsetDateTime decisionDate,
        String decisionType,
        String skipReason,
        String policyName,
        int policyVersion,
        BigDecimal currentPrice,
        BigDecimal targetPrice,
        BigDecimal priceChangePct,
        String actionStatus,
        String executionMode,
        BigDecimal actualPrice,
        String reconciliationSource,
        String explanationSummary
) {
}
