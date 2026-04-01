package io.datapulse.pricing.api;

import java.math.BigDecimal;
import java.util.List;

public record BulkManualPreviewResponse(
        Summary summary,
        List<OfferPreview> offers
) {

    public record Summary(
            int totalRequested,
            int willChange,
            int willSkip,
            int willBlock,
            BigDecimal avgChangePct,
            BigDecimal minMarginAfter,
            BigDecimal maxChangePct
    ) {
    }

    public record OfferPreview(
            Long marketplaceOfferId,
            String skuCode,
            String productName,
            BigDecimal currentPrice,
            BigDecimal requestedPrice,
            BigDecimal effectivePrice,
            String result,
            List<ConstraintApplied> constraintsApplied,
            BigDecimal projectedMarginPct,
            String skipReason,
            String guard
    ) {
    }

    public record ConstraintApplied(
            String name,
            BigDecimal fromPrice,
            BigDecimal toPrice
    ) {
    }
}
