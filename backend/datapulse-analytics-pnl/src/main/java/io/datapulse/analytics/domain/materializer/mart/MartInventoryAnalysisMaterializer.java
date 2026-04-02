package io.datapulse.analytics.domain.materializer.mart;

import java.time.Instant;

import io.datapulse.analytics.config.AnalyticsQueryProperties;
import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MartInventoryAnalysisMaterializer implements AnalyticsMaterializer {

  private static final String TABLE = "mart_inventory_analysis";

  private static final String FULL_MATERIALIZE_SQL = """
      INSERT INTO mart_inventory_analysis
      SELECT
          inv.connection_id,
          inv.source_platform,
          inv.product_id,
          dp.seller_sku_id,
          inv.warehouse_id,
          inv.analysis_date,
          inv.available,
          inv.reserved,
          sales_agg.avg_daily_sales,
          if(sales_agg.avg_daily_sales IS NOT NULL AND sales_agg.avg_daily_sales > 0,
             cast(
                 cast(inv.available AS Decimal(18, 4)) / sales_agg.avg_daily_sales
                 AS Decimal(18, 1)),
             NULL) AS days_of_cover,
          multiIf(
              sales_agg.avg_daily_sales IS NULL OR sales_agg.avg_daily_sales = 0, 'NORMAL',
              cast(cast(inv.available AS Decimal(18, 4)) / sales_agg.avg_daily_sales AS Decimal(18, 4))
                  < %d, 'CRITICAL',
              cast(cast(inv.available AS Decimal(18, 4)) / sales_agg.avg_daily_sales AS Decimal(18, 4))
                  < %d, 'WARNING',
              'NORMAL'
          ) AS stock_out_risk,
          fpc.cost_price,
          if(fpc.cost_price IS NOT NULL AND sales_agg.avg_daily_sales IS NOT NULL
             AND cast(inv.available AS Decimal(18, 4)) > sales_agg.avg_daily_sales * %d,
             toDecimal128(
                 toInt64(inv.available - sales_agg.avg_daily_sales * %d) * fpc.cost_price,
                 2),
             NULL) AS frozen_capital,
          if(sales_agg.avg_daily_sales IS NOT NULL AND sales_agg.avg_daily_sales > 0,
             greatest(toInt32(0), toInt32(sales_agg.avg_daily_sales * %d - inv.available)),
             NULL) AS recommended_replenishment,
          %d AS ver
      FROM (
          SELECT
              connection_id,
              source_platform,
              product_id,
              warehouse_id,
              max(captured_date) AS analysis_date,
              argMax(available, captured_at) AS available,
              argMax(reserved, captured_at) AS reserved
          FROM fact_inventory_snapshot
          GROUP BY connection_id, source_platform, product_id, warehouse_id
      ) inv
      LEFT JOIN dim_product AS dp
          ON inv.product_id = dp.product_id AND inv.connection_id = dp.connection_id
      LEFT JOIN (
          SELECT
              product_id,
              connection_id,
              cast(sum(quantity) AS Decimal(18, 2)) / toDecimal32(%d, 0) AS avg_daily_sales
          FROM fact_sales
          WHERE sale_date >= today() - %d
          GROUP BY product_id, connection_id
      ) sales_agg ON inv.product_id = sales_agg.product_id
          AND inv.connection_id = sales_agg.connection_id
      LEFT JOIN fact_product_cost AS fpc
          ON dp.seller_sku_id = fpc.seller_sku_id
          AND today() >= fpc.valid_from
          AND (fpc.valid_to IS NULL OR today() < fpc.valid_to)
      SETTINGS final = 1
      """;

  private final MaterializationJdbc jdbc;
  private final AnalyticsQueryProperties queryProperties;

  @Override
  public void materializeFull() {
    jdbc.ch().execute("TRUNCATE TABLE " + TABLE);

    var inv = queryProperties.inventory();
    long ver = Instant.now().toEpochMilli();

    String sql = FULL_MATERIALIZE_SQL.formatted(
        inv.leadTimeDays(),
        inv.leadTimeDays() * 2,
        inv.targetDaysOfCover(),
        inv.targetDaysOfCover(),
        inv.targetDaysOfCover(),
        ver,
        inv.velocityWindowDays(),
        inv.velocityWindowDays()
    );
    jdbc.ch().execute(sql);

    Long count = jdbc.ch().queryForObject("SELECT count() FROM " + TABLE, Long.class);
    log.info("Materialized mart_inventory_analysis: rows={}", count);
  }

  @Override
  public void materializeIncremental(long jobExecutionId) {
    materializeFull();
  }

  @Override
  public String tableName() {
    return TABLE;
  }

  @Override
  public MaterializationPhase phase() {
    return MaterializationPhase.MART;
  }
}
