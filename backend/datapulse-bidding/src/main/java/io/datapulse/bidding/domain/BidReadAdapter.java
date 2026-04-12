package io.datapulse.bidding.domain;

import java.util.Map;

/**
 * Port for reading current bid state from a marketplace.
 * Each marketplace has its own implementation in the adapter layer.
 * All bid values are in marketplace-native units
 * (kopecks for WB/Ozon, percent x100 for Yandex).
 */
public interface BidReadAdapter {

  /**
   * Reads current bid information for a single product.
   *
   * @param campaignExternalId marketplace campaign ID (may be null for Yandex)
   * @param marketplaceSku     external SKU identifier
   * @param connectionId       connection ID for rate limiting
   * @param credentials        API credentials from vault
   * @return bid info if available, or {@link BidReadResult#empty()} on failure
   */
  BidReadResult readCurrentBid(
      String campaignExternalId,
      String marketplaceSku,
      long connectionId,
      Map<String, String> credentials);

  String marketplaceType();
}
