package io.datapulse.pricing.api;

import java.util.List;

public record BulkManualApplyResponse(
        Long pricingRunId,
        int created,
        int skipped,
        List<String> errors
) {
}
