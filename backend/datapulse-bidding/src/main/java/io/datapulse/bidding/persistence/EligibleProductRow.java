package io.datapulse.bidding.persistence;

/**
 * @param marketplaceOfferId PG marketplace_offer.id
 * @param marketplaceSku     external marketplace SKU (e.g. nmId for WB)
 * @param connectionId       marketplace_connection.id
 */
public record EligibleProductRow(
    long marketplaceOfferId,
    String marketplaceSku,
    long connectionId
) {
}
