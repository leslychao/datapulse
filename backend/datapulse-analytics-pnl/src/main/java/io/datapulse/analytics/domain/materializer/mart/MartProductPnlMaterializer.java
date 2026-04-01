package io.datapulse.analytics.domain.materializer.mart;

import java.time.Instant;

import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import io.datapulse.analytics.domain.MaterializationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Materializes mart_product_pnl from mart_posting_pnl + fact_finance (PRODUCT, ACCOUNT level).
 *
 * <p>Three sources merged:</p>
 * <ul>
 *   <li>POSTING level: rollup of mart_posting_pnl per (seller_sku_id, period)</li>
 *   <li>PRODUCT level: fact_finance entries with attribution_level = 'PRODUCT'</li>
 *   <li>ACCOUNT level: fact_finance entries with attribution_level = 'ACCOUNT'</li>
 * </ul>
 *
 * <p>COGS at product-month level uses cross-posting revenue-ratio netting (T-4/T-7).</p>
 * <p>Advertising cost = 0 in Phase B core (fallback until advertising ingestion).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MartProductPnlMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "mart_product_pnl";

    private static final String FULL_MATERIALIZE_SQL = """
            INSERT INTO mart_product_pnl
            SELECT
                connection_id,
                source_platform,
                seller_sku_id,
                product_id,
                period,
                attribution_level,
                revenue_amount,
                marketplace_commission_amount,
                acquiring_commission_amount,
                logistics_cost_amount,
                storage_cost_amount,
                penalties_amount,
                marketing_cost_amount,
                acceptance_cost_amount,
                other_marketplace_charges_amount,
                compensation_amount,
                refund_amount,
                net_payout,
                gross_cogs,
                -- product_refund_ratio at product x month level
                if(revenue_amount != 0,
                   abs(refund_amount) / revenue_amount,
                   NULL) AS product_refund_ratio,
                -- net_cogs = gross_cogs * (1 - product_refund_ratio)
                if(gross_cogs IS NOT NULL,
                   gross_cogs * greatest(toDecimal128(0, 4), 1 - coalesce(
                       if(revenue_amount != 0,
                          abs(refund_amount) / revenue_amount,
                          toDecimal128(0, 4)),
                       toDecimal128(0, 4))),
                   NULL) AS net_cogs,
                -- cogs_status: worst of posting statuses
                cogs_status,
                -- advertising_cost: Phase B core = 0
                toDecimal64(0, 2) AS advertising_cost,
                -- marketplace_pnl = sum of all 11 signed measures
                revenue_amount + marketplace_commission_amount + acquiring_commission_amount
                    + logistics_cost_amount + storage_cost_amount + penalties_amount
                    + marketing_cost_amount + acceptance_cost_amount
                    + other_marketplace_charges_amount + compensation_amount
                    + refund_amount AS marketplace_pnl,
                -- full_pnl = marketplace_pnl - advertising - net_cogs
                if(gross_cogs IS NOT NULL,
                   (revenue_amount + marketplace_commission_amount + acquiring_commission_amount
                       + logistics_cost_amount + storage_cost_amount + penalties_amount
                       + marketing_cost_amount + acceptance_cost_amount
                       + other_marketplace_charges_amount + compensation_amount
                       + refund_amount)
                   - toDecimal64(0, 2)
                   - (gross_cogs * greatest(toDecimal128(0, 4), 1 - coalesce(
                       if(revenue_amount != 0,
                          abs(refund_amount) / revenue_amount,
                          toDecimal128(0, 4)),
                       toDecimal128(0, 4)))),
                   NULL) AS full_pnl,
                %d AS ver
            FROM (
                -- Source 1: Rollup of mart_posting_pnl → PRODUCT rows
                SELECT
                    connection_id,
                    source_platform,
                    coalesce(seller_sku_id, 0) AS seller_sku_id,
                    coalesce(product_id, 0) AS product_id,
                    toYYYYMM(finance_date) AS period,
                    'PRODUCT' AS attribution_level,
                    sum(revenue_amount) AS revenue_amount,
                    sum(marketplace_commission_amount) AS marketplace_commission_amount,
                    sum(acquiring_commission_amount) AS acquiring_commission_amount,
                    sum(logistics_cost_amount) AS logistics_cost_amount,
                    sum(storage_cost_amount) AS storage_cost_amount,
                    sum(penalties_amount) AS penalties_amount,
                    sum(marketing_cost_amount) AS marketing_cost_amount,
                    sum(acceptance_cost_amount) AS acceptance_cost_amount,
                    sum(other_marketplace_charges_amount) AS other_marketplace_charges_amount,
                    sum(compensation_amount) AS compensation_amount,
                    sum(refund_amount) AS refund_amount,
                    sum(net_payout) AS net_payout,
                    sumIf(gross_cogs, cogs_status = 'OK') AS gross_cogs,
                    multiIf(
                        countIf(cogs_status = 'NO_SALES') > 0 AND countIf(cogs_status = 'OK') = 0, 'NO_SALES',
                        countIf(cogs_status = 'NO_COST_PROFILE') > 0, 'NO_COST_PROFILE',
                        'OK'
                    ) AS cogs_status
                FROM mart_posting_pnl
                GROUP BY connection_id, source_platform,
                         coalesce(seller_sku_id, 0), coalesce(product_id, 0),
                         toYYYYMM(finance_date)

                UNION ALL

                -- Source 2: PRODUCT-level fact_finance entries (not in mart_posting_pnl)
                SELECT
                    connection_id,
                    any(source_platform) AS source_platform,
                    coalesce(seller_sku_id, 0) AS seller_sku_id,
                    0 AS product_id,
                    toYYYYMM(finance_date) AS period,
                    'PRODUCT' AS attribution_level,
                    sum(revenue_amount) AS revenue_amount,
                    sum(marketplace_commission_amount) AS marketplace_commission_amount,
                    sum(acquiring_commission_amount) AS acquiring_commission_amount,
                    sum(logistics_cost_amount) AS logistics_cost_amount,
                    sum(storage_cost_amount) AS storage_cost_amount,
                    sum(penalties_amount) AS penalties_amount,
                    sum(marketing_cost_amount) AS marketing_cost_amount,
                    sum(acceptance_cost_amount) AS acceptance_cost_amount,
                    sum(other_marketplace_charges_amount) AS other_marketplace_charges_amount,
                    sum(compensation_amount) AS compensation_amount,
                    sum(refund_amount) AS refund_amount,
                    sum(net_payout) AS net_payout,
                    NULL AS gross_cogs,
                    'NO_SALES' AS cogs_status
                FROM fact_finance
                WHERE attribution_level = 'PRODUCT'
                GROUP BY connection_id, coalesce(seller_sku_id, 0), toYYYYMM(finance_date)

                UNION ALL

                -- Source 3: ACCOUNT-level fact_finance entries
                SELECT
                    connection_id,
                    any(source_platform) AS source_platform,
                    0 AS seller_sku_id,
                    0 AS product_id,
                    toYYYYMM(finance_date) AS period,
                    'ACCOUNT' AS attribution_level,
                    sum(revenue_amount) AS revenue_amount,
                    sum(marketplace_commission_amount) AS marketplace_commission_amount,
                    sum(acquiring_commission_amount) AS acquiring_commission_amount,
                    sum(logistics_cost_amount) AS logistics_cost_amount,
                    sum(storage_cost_amount) AS storage_cost_amount,
                    sum(penalties_amount) AS penalties_amount,
                    sum(marketing_cost_amount) AS marketing_cost_amount,
                    sum(acceptance_cost_amount) AS acceptance_cost_amount,
                    sum(other_marketplace_charges_amount) AS other_marketplace_charges_amount,
                    sum(compensation_amount) AS compensation_amount,
                    sum(refund_amount) AS refund_amount,
                    sum(net_payout) AS net_payout,
                    NULL AS gross_cogs,
                    'NO_SALES' AS cogs_status
                FROM fact_finance
                WHERE attribution_level = 'ACCOUNT'
                GROUP BY connection_id, toYYYYMM(finance_date)
            )
            SETTINGS final = 1
            """;

    private final MaterializationJdbc jdbc;

    @Override
    public void materializeFull() {
        jdbc.ch().execute("TRUNCATE TABLE " + TABLE);

        long ver = Instant.now().toEpochMilli();
        jdbc.ch().execute(FULL_MATERIALIZE_SQL.formatted(ver));

        Long count = jdbc.ch().queryForObject("SELECT count() FROM " + TABLE, Long.class);
        log.info("Materialized mart_product_pnl: rows={}", count);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        // Product-level mart benefits from full re-materialization for correctness
        // (cross-posting COGS netting, period aggregation).
        // Incremental = full rebuild of affected periods.
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
