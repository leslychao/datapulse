package io.datapulse.bidding.domain;

/**
 * Unit of bid values in marketplace-native format.
 */
public enum BidUnit {

  /** WB and Ozon: absolute value in kopecks */
  KOPECKS,

  /** Yandex: percent of item cost × 100 (e.g. 570 = 5.7%) */
  PERCENT_X100
}
