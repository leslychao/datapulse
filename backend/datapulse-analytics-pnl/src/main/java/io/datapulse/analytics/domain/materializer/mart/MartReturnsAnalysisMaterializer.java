package io.datapulse.analytics.domain.materializer.mart;

import java.time.Instant;

import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import io.datapulse.analytics.domain.MaterializationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Materializes mart_returns_analysis from fact_returns + fact_sales.
 *
 * <p>Aggregates operational return metrics per (connection_id, seller_sku_id, period):
 * return count/quantity/amount, sale count/quantity, return rate,
 * and top return reason. Financial impact (refund, penalties) lives
 * exclusively in P&L marts.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MartReturnsAnalysisMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "mart_returns_analysis";

    private static final String FULL_MATERIALIZE_SQL = """
            INSERT INTO %s
            SELECT
                r.workspace_id,
                r.connection_id,
                r.source_platform,
                r.product_id,
                r.seller_sku_id,
                r.period,
                r.return_count,
                r.return_quantity,
                r.return_amount,
                coalesce(s.sale_count, 0) AS sale_count,
                coalesce(s.sale_quantity, 0) AS sale_quantity,
                if(s.sale_quantity IS NOT NULL AND s.sale_quantity > 0,
                   toDecimal64(r.return_quantity, 2) / s.sale_quantity * 100,
                   NULL) AS return_rate_pct,
                r.top_return_reason,
                r.distinct_reason_count,
                %d AS ver
            FROM (
                SELECT
                    connection_id,
                    any(workspace_id) AS workspace_id,
                    any(source_platform) AS source_platform,
                    coalesce(product_id, toUInt64(0)) AS product_id,
                    coalesce(seller_sku_id, toUInt64(0)) AS seller_sku_id,
                    toYYYYMM(return_date) AS period,
                    count() AS return_count,
                    sum(quantity) AS return_quantity,
                    sum(ifNull(return_amount, toDecimal64(0, 2))) AS return_amount,
                    topK(1)(return_reason)[1] AS top_return_reason,
                    uniqExact(return_reason) AS distinct_reason_count
                FROM fact_returns
                GROUP BY connection_id, product_id, seller_sku_id, toYYYYMM(return_date)
            ) r
            LEFT JOIN (
                SELECT
                    connection_id,
                    coalesce(product_id, toUInt64(0)) AS product_id,
                    coalesce(seller_sku_id, toUInt64(0)) AS seller_sku_id,
                    toYYYYMM(sale_date) AS period,
                    count() AS sale_count,
                    sum(quantity) AS sale_quantity
                FROM fact_sales
                GROUP BY connection_id, product_id, seller_sku_id, toYYYYMM(sale_date)
            ) s ON r.connection_id = s.connection_id
                AND r.product_id = s.product_id
                AND r.seller_sku_id = s.seller_sku_id
                AND r.period = s.period
            SETTINGS final = 1
            """;

    private final MaterializationJdbc jdbc;

    @Override
    public void materializeFull() {
        long ver = Instant.now().toEpochMilli();

        jdbc.fullMaterializeWithSwap(TABLE, staging -> {
            jdbc.ch().execute(FULL_MATERIALIZE_SQL.formatted(staging, ver));
        });

        Long count = jdbc.ch().queryForObject("SELECT count() FROM " + TABLE, Long.class);
        log.info("Materialized mart_returns_analysis: rows={}", count);
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
