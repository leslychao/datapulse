package io.datapulse.analytics.domain.materializer.mart;

import java.time.Instant;

import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.domain.MaterializationJdbc;
import io.datapulse.analytics.domain.MaterializationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Materializes mart_inventory_analysis from fact_inventory_snapshot + fact_sales + fact_product_cost.
 *
 * <p>Computes days-of-cover, stock-out risk, frozen capital, and replenishment recommendations
 * based on configurable thresholds.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MartInventoryAnalysisMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "mart_inventory_analysis";

    private static final int VELOCITY_WINDOW_DAYS = 14;
    private static final int TARGET_DAYS_OF_COVER = 30;
    private static final int LEAD_TIME_DAYS = 7;

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
                -- avg_daily_sales_14d
                sales_agg.avg_daily_sales,
                -- days_of_cover = available / avg_daily_sales
                if(sales_agg.avg_daily_sales IS NOT NULL AND sales_agg.avg_daily_sales > 0,
                   toDecimal64(inv.available, 1) / sales_agg.avg_daily_sales,
                   NULL) AS days_of_cover,
                -- stock_out_risk
                multiIf(
                    sales_agg.avg_daily_sales IS NULL OR sales_agg.avg_daily_sales = 0, 'NORMAL',
                    toDecimal64(inv.available, 1) / sales_agg.avg_daily_sales < %d, 'CRITICAL',
                    toDecimal64(inv.available, 1) / sales_agg.avg_daily_sales < %d, 'WARNING',
                    'NORMAL'
                ) AS stock_out_risk,
                -- cost_price from SCD2
                fpc.cost_price,
                -- frozen_capital = excess_qty * cost_price
                if(fpc.cost_price IS NOT NULL AND sales_agg.avg_daily_sales IS NOT NULL
                   AND inv.available > sales_agg.avg_daily_sales * %d,
                   toDecimal64(
                       toInt32(inv.available - sales_agg.avg_daily_sales * %d), 2
                   ) * fpc.cost_price,
                   NULL) AS frozen_capital,
                -- recommended_replenishment
                if(sales_agg.avg_daily_sales IS NOT NULL AND sales_agg.avg_daily_sales > 0,
                   greatest(0, toInt32(sales_agg.avg_daily_sales * %d - inv.available)),
                   NULL) AS recommended_replenishment,
                %d AS ver
            FROM (
                -- Latest snapshot per product x warehouse
                SELECT
                    connection_id,
                    source_platform,
                    product_id,
                    warehouse_id,
                    max(captured_date) AS analysis_date,
                    argMax(available, captured_at) AS available,
                    argMax(reserved, captured_at) AS reserved
                FROM fact_inventory_snapshot FINAL
                GROUP BY connection_id, source_platform, product_id, warehouse_id
            ) inv
            LEFT JOIN dim_product FINAL dp
                ON inv.product_id = dp.product_id AND inv.connection_id = dp.connection_id
            -- 14-day average daily sales
            LEFT JOIN (
                SELECT
                    product_id,
                    connection_id,
                    toDecimal64(sum(quantity), 2) / %d AS avg_daily_sales
                FROM fact_sales FINAL
                WHERE sale_date >= today() - %d
                GROUP BY product_id, connection_id
            ) sales_agg ON inv.product_id = sales_agg.product_id
                AND inv.connection_id = sales_agg.connection_id
            -- Current cost price (SCD2)
            LEFT JOIN fact_product_cost FINAL fpc
                ON dp.seller_sku_id = fpc.seller_sku_id
                AND today() >= fpc.valid_from
                AND (fpc.valid_to IS NULL OR today() < fpc.valid_to)
            """;

    private final MaterializationJdbc jdbc;

    @Override
    public void materializeFull() {
        jdbc.ch().execute("TRUNCATE TABLE " + TABLE);

        long ver = Instant.now().toEpochMilli();
        String sql = FULL_MATERIALIZE_SQL.formatted(
                LEAD_TIME_DAYS,
                LEAD_TIME_DAYS * 2,
                TARGET_DAYS_OF_COVER,
                TARGET_DAYS_OF_COVER,
                TARGET_DAYS_OF_COVER,
                ver,
                VELOCITY_WINDOW_DAYS,
                VELOCITY_WINDOW_DAYS
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
