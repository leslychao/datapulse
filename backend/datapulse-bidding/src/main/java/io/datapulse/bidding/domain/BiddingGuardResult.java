package io.datapulse.bidding.domain;

import java.util.Map;

/**
 * Result of a single bidding guard check.
 *
 * @param allowed    whether the guard allows the proposed bid change
 * @param guardName  technical name of the guard (e.g. "economy_guard")
 * @param messageKey i18n message key (null if allowed)
 * @param args       interpolation parameters for the message key (nullable)
 */
public record BiddingGuardResult(
    boolean allowed,
    String guardName,
    String messageKey,
    Map<String, Object> args
) {

  public static BiddingGuardResult allow(String guardName) {
    return new BiddingGuardResult(true, guardName, null, null);
  }

  public static BiddingGuardResult block(String guardName, String messageKey) {
    return new BiddingGuardResult(false, guardName, messageKey, null);
  }

  public static BiddingGuardResult block(
      String guardName, String messageKey, Map<String, Object> args) {
    return new BiddingGuardResult(false, guardName, messageKey, args);
  }
}
