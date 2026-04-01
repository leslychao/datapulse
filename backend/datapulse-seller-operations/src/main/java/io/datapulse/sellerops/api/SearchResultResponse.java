package io.datapulse.sellerops.api;

public record SearchResultResponse(
    long offerId,
    String sku,
    String productName,
    String marketplaceType,
    String connectionName) {}
