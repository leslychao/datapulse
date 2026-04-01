package io.datapulse.execution.domain;

import io.datapulse.integration.domain.MarketplaceType;

import java.util.Map;

/**
 * Resolved context for executing a price action on a marketplace offer.
 * Contains everything needed by write/read adapters: connection, marketplace type,
 * SKU identifiers, and API credentials.
 */
public record OfferExecutionContext(
        long offerId,
        long connectionId,
        long workspaceId,
        MarketplaceType marketplaceType,
        String marketplaceSku,
        String marketplaceSkuAlt,
        Map<String, String> credentials
) {
}
