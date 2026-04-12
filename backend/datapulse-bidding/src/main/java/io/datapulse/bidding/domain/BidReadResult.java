package io.datapulse.bidding.domain;

/**
 * Result of reading bid information from a marketplace API.
 * All values are in marketplace-native units.
 *
 * @param currentBid     current bid value (null if unknown)
 * @param minBid         marketplace minimum allowed bid (null if unknown)
 * @param competitiveBid average competitive bid (null if unavailable)
 * @param leadersBid     leaders' bid value (null if unavailable)
 */
public record BidReadResult(
    Integer currentBid,
    Integer minBid,
    Integer competitiveBid,
    Integer leadersBid
) {

  public static BidReadResult empty() {
    return new BidReadResult(null, null, null, null);
  }

  public static BidReadResult of(Integer currentBid, Integer minBid,
      Integer competitiveBid, Integer leadersBid) {
    return new BidReadResult(currentBid, minBid, competitiveBid, leadersBid);
  }
}
