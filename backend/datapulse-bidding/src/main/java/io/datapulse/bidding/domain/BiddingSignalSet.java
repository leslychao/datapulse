package io.datapulse.bidding.domain;

import java.math.BigDecimal;

/**
 * Assembled signals for a single marketplace offer at bid decision time.
 * All wrapper-type fields are nullable — absence of a signal is meaningful
 * (e.g. missing margin → guard blocks BID_UP).
 *
 * @param currentBid            current bid in marketplace-native units
 * @param drrPct                ad cost share of revenue (%)
 * @param cpoPct                cost per order as % of revenue
 * @param roas                  return on ad spend
 * @param impressions           total ad impressions in lookback window
 * @param clicks                total ad clicks in lookback window
 * @param adOrders              total orders attributed to ads in lookback window
 * @param adSpend               total ad spend in roubles in lookback window
 * @param marginPct             product margin (%)
 * @param stockDays             days of cover (inventory)
 * @param competitiveBid        average competitive bid (marketplace-native units)
 * @param leadersBid            leaders' bid (marketplace-native units)
 * @param minBid                marketplace minimum allowed bid (marketplace-native units)
 * @param previousDecisionType  decision type from the most recent bidding run (nullable)
 * @param hoursSinceLastChange  hours since the bid was last changed (nullable)
 * @param campaignStatus        status of the advertising campaign
 * @param bidUnit               unit of bid values: KOPECKS (WB/Ozon) or PERCENT_X100 (Yandex)
 */
public record BiddingSignalSet(
    Integer currentBid,
    BigDecimal drrPct,
    BigDecimal cpoPct,
    BigDecimal roas,
    long impressions,
    long clicks,
    long adOrders,
    BigDecimal adSpend,
    BigDecimal marginPct,
    Integer stockDays,
    Integer competitiveBid,
    Integer leadersBid,
    Integer minBid,
    BidDecisionType previousDecisionType,
    Integer hoursSinceLastChange,
    String campaignStatus,
    BidUnit bidUnit
) {
}
