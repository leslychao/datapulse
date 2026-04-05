package io.datapulse.etl.persistence.clickhouse;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Flat row for {@code fact_advertising} in ClickHouse.
 * Produced by flatteners (WB/Ozon), consumed by {@link AdvertisingClickHouseWriter}.
 */
public record AdvertisingFactRow(
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
) {}
