package io.datapulse.execution.domain;

import io.datapulse.integration.domain.MarketplaceType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Marketplace-specific adapter for writing (setting) a price.
 * Each marketplace implementation handles API specifics:
 * WB uses async upload + poll pattern, Ozon uses synchronous response.
 */
public interface PriceWriteAdapter {

    MarketplaceType marketplace();

    PriceWriteResult setPrice(long connectionId,
                              String marketplaceSku,
                              BigDecimal targetPrice,
                              Map<String, String> credentials);
}
