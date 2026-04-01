package io.datapulse.execution.domain.adapter;

import java.math.BigDecimal;

/**
 * Result of reading current price from marketplace API.
 * Used for reconciliation — verifying that a price change was actually applied.
 */
public record PriceReadResult(
        BigDecimal currentPrice,
        String rawSnapshot
) {
}
