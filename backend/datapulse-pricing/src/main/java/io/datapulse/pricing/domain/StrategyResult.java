package io.datapulse.pricing.domain;

import java.math.BigDecimal;

/**
 * Raw output from strategy evaluation before constraint resolution and guard pipeline.
 *
 * @param rawTargetPrice computed target price (null when strategy cannot compute — HOLD/SKIP)
 * @param explanation    structured explanation for explanation_summary (audit trail)
 * @param reasonKey      i18n message key for skip_reason (null when rawTargetPrice is present)
 */
public record StrategyResult(
        BigDecimal rawTargetPrice,
        String explanation,
        String reasonKey
) {

    public static StrategyResult success(BigDecimal rawTargetPrice, String explanation) {
        return new StrategyResult(rawTargetPrice, explanation, null);
    }

    public static StrategyResult skip(String explanation, String reasonKey) {
        return new StrategyResult(null, explanation, reasonKey);
    }
}
