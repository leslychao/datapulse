package io.datapulse.etl.persistence.clickhouse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Flat row for {@code fact_advertising} in ClickHouse.
 * Produced by flatteners (WB/Ozon), consumed by {@link AdvertisingClickHouseWriter}.
 */
public record AdvertisingFactRow(
    long workspaceId,
    long connectionId,
    String sourcePlatform,
    long campaignId,
    LocalDate adDate,
    String marketplaceSku,
    long views,
    long clicks,
    BigDecimal spend,
    int orders,
    int orderedUnits,
    BigDecimal orderedRevenue,
    int canceled,
    float ctr,
    BigDecimal cpc,
    float cr,
    long jobExecutionId
) {

  public static AdvertisingFactRow fromOzon(
      long workspaceId, long connectionId, long campaignId, LocalDate adDate,
      String sku, long views, long clicks, BigDecimal spend,
      int orders, BigDecimal revenue, long jobExecutionId) {
    float ctr = views > 0 ? (float) clicks / views : 0f;
    BigDecimal cpc = clicks > 0
        ? spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    float cr = clicks > 0 ? (float) orders / clicks : 0f;

    return new AdvertisingFactRow(
        workspaceId, connectionId, "OZON", campaignId, adDate, sku,
        views, clicks, spend, orders, orders, revenue,
        0, ctr, cpc, cr, jobExecutionId);
  }
}
