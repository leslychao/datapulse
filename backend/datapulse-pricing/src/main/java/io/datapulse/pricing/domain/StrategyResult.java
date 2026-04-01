package io.datapulse.pricing.domain;

import java.math.BigDecimal;

/**
 * Raw output from strategy evaluation before constraint resolution and guard pipeline.
 *
 * @param rawTargetPrice computed target price (before constraints and rounding)
 * @param explanation    structured explanation of how the price was computed
 */
public record StrategyResult(
        BigDecimal rawTargetPrice,
        String explanation
) {
}
