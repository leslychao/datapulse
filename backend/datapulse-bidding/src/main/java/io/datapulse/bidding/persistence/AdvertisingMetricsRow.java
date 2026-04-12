package io.datapulse.bidding.persistence;

import java.math.BigDecimal;

public record AdvertisingMetricsRow(
    BigDecimal drrPct,
    BigDecimal cpoPct,
    BigDecimal roas,
    BigDecimal totalSpend,
    long impressions,
    long clicks,
    long adOrders
) {
}
