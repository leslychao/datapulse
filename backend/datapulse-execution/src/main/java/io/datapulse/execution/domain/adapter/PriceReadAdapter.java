package io.datapulse.execution.domain.adapter;

import io.datapulse.integration.domain.MarketplaceType;

import java.util.Map;

/**
 * Marketplace-specific adapter for reading the current price.
 * Used by reconciliation to verify that a price change was applied.
 */
public interface PriceReadAdapter {

    MarketplaceType marketplace();

    PriceReadResult readCurrentPrice(long connectionId,
                                     String marketplaceSku,
                                     Map<String, String> credentials);
}
