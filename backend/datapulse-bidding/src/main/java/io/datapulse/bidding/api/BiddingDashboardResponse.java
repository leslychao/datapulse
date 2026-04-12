package io.datapulse.bidding.api;

import java.util.List;
import java.util.Map;

public record BiddingDashboardResponse(
    int totalManagedProducts,
    int activePolicies,
    Map<String, Integer> productsByStrategy,
    Map<String, Integer> decisionsByType,
    int totalRunsLast7d,
    int failedRunsLast7d,
    int pausedRunsLast7d,
    List<TopProductItem> topHighDrr,
    List<TopProductItem> topImproved
) {

  public record TopProductItem(
      long marketplaceOfferId,
      String marketplaceSku,
      String strategyType,
      String lastDecisionType,
      Integer currentBid,
      java.math.BigDecimal drrPct
  ) {}
}
