package io.datapulse.analytics.domain.materializer.mart;

import java.time.Instant;

import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import io.datapulse.analytics.domain.MaterializationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Materializes mart_posting_pnl from fact_finance + fact_sales + fact_product_cost.
 * Implements the full P&L formula with COGS netting and reconciliation residual.
 *
 * <p>Acquiring allocation: acquiring entries (posting_id IS NULL, order_id IS NOT NULL)
 * are distributed pro-rata by revenue across postings within the same order.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MartPostingPnlMaterializer implements AnalyticsMaterializer {

  private static final String TABLE = "mart_posting_pnl";
  private static final String INCR_MARKER = "/*INCR*/";

  /**
   * Full materialization SQL: aggregates fact_finance POSTING-level entries
   * per posting, joins fact_sales for quantity, fact_product_cost for COGS,
   * allocates acquiring pro-rata, computes reconciliation_residual.
   *
   * <p>The {@code INSERT INTO %s} placeholder is replaced with the target table
   * (staging during full re-materialization, live during incremental).</p>
   */
  private static final String FULL_MATERIALIZE_SQL = """
      INSERT INTO %s
      SELECT
          pm.workspace_id,
          pm.posting_id,
          pm.connection_id,
          pm.source_platform,
          pm.fulfillment_type,
          pm.order_id,
          coalesce(pm.seller_sku_id, fs.sales_seller_sku_id) AS seller_sku_id,
          dp.product_id,
          pm.finance_date,
          pm.revenue_amount,
          pm.marketplace_commission_amount,
          -- allocated acquiring: posting's own + pro-rata share of order-level acquiring
          pm.acquiring_commission_amount + coalesce(
              aq.order_acquiring * pm.revenue_amount / nullIf(orv.order_revenue, 0),
              0
          ) AS acquiring_commission_amount,
          pm.logistics_cost_amount,
          pm.storage_cost_amount,
          pm.penalties_amount,
          pm.marketing_cost_amount,
          pm.acceptance_cost_amount,
          pm.other_marketplace_charges_amount,
          pm.compensation_amount,
          pm.refund_amount,
          -- allocated net_payout mirrors acquiring allocation
          pm.net_payout + coalesce(
              aq.order_acquiring_payout * pm.revenue_amount / nullIf(orv.order_revenue, 0),
              0
          ) AS net_payout,
          s.quantity,
          -- gross_cogs = quantity * cost_price (NULL if either missing)
          if(s.quantity IS NOT NULL AND fpc_cost.cost_price IS NOT NULL,
             toDecimal64(s.quantity, 2) * fpc_cost.cost_price,
             NULL) AS gross_cogs,
          -- refund_ratio = |refund_amount| / revenue_amount (uniform Decimal(18,4) branches)
          if(pm.revenue_amount != 0,
             toDecimal128(abs(pm.refund_amount) / pm.revenue_amount, 4),
             CAST(NULL AS Nullable(Decimal(18, 4)))) AS refund_ratio,
          -- net_cogs = gross_cogs * (1 - refund_ratio)
          if(s.quantity IS NOT NULL AND fpc_cost.cost_price IS NOT NULL,
             (toDecimal64(s.quantity, 2) * fpc_cost.cost_price)
                 * greatest(
                     toDecimal128(0, 4),
                     toDecimal128(1, 4) - coalesce(
                         if(pm.revenue_amount != 0,
                            toDecimal128(abs(pm.refund_amount) / pm.revenue_amount, 4),
                            toDecimal128(0, 4)),
                         toDecimal128(0, 4))),
             NULL) AS net_cogs,
          -- cogs_status
          multiIf(
              s.quantity IS NULL, 'NO_SALES',
              fpc_cost.cost_price IS NULL, 'NO_COST_PROFILE',
              'OK'
          ) AS cogs_status,
          -- reconciliation_residual = net_payout - sum(all named components)
          (pm.net_payout + coalesce(
              aq.order_acquiring_payout * pm.revenue_amount / nullIf(orv.order_revenue, 0), 0)
          ) - (
              pm.revenue_amount
              + pm.marketplace_commission_amount
              + (pm.acquiring_commission_amount + coalesce(
                  aq.order_acquiring * pm.revenue_amount / nullIf(orv.order_revenue, 0), 0))
              + pm.logistics_cost_amount
              + pm.storage_cost_amount
              + pm.penalties_amount
              + pm.marketing_cost_amount
              + pm.acceptance_cost_amount
              + pm.other_marketplace_charges_amount
              + pm.compensation_amount
              + pm.refund_amount
          ) AS reconciliation_residual,
          coalesce(
              nullIf(trim(p_by_id.sku_code), ''),
              nullIf(trim(p_by_sku.sku_code), ''),
              nullIf(trim(p_by_id.marketplace_sku), ''),
              nullIf(trim(p_by_sku.marketplace_sku), '')
          ) AS sku_code,
          coalesce(
              nullIf(trim(p_by_id.product_name), ''),
              nullIf(trim(p_by_sku.product_name), '')
          ) AS product_name,
          coalesce(
              nullIf(trim(p_by_id.marketplace_sku), ''),
              nullIf(trim(p_by_sku.marketplace_sku), '')
          ) AS marketplace_sku,
          %d AS ver
      FROM (
          -- Aggregate posting-level entries per posting
          SELECT
              posting_id,
              connection_id,
              any(workspace_id) AS workspace_id,
              any(source_platform) AS source_platform,
              any(fulfillment_type) AS fulfillment_type,
              any(order_id) AS order_id,
              any(seller_sku_id) AS seller_sku_id,
              coalesce(
                  minIf(finance_date, entry_type = 'SALE_ACCRUAL'),
                  min(finance_date)
              ) AS finance_date,
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
              sum(net_payout) AS net_payout
          FROM fact_finance
          WHERE attribution_level = 'POSTING'
            AND posting_id IS NOT NULL
            AND posting_id != ''
          GROUP BY posting_id, connection_id /*INCR*/
      ) pm
      -- Acquiring: order-level entries allocated pro-rata by revenue
      LEFT JOIN (
          SELECT
              order_id,
              connection_id,
              sum(acquiring_commission_amount) AS order_acquiring,
              sum(net_payout) AS order_acquiring_payout
          FROM fact_finance
          WHERE attribution_level = 'POSTING'
            AND posting_id IS NULL
            AND order_id IS NOT NULL
            AND order_id != ''
          GROUP BY order_id, connection_id
      ) aq ON pm.order_id = aq.order_id AND pm.connection_id = aq.connection_id
      -- Order-level revenue for pro-rata denominator
      LEFT JOIN (
          SELECT
              order_id,
              connection_id,
              sum(revenue_amount) AS order_revenue
          FROM (
              SELECT posting_id, connection_id, any(order_id) AS order_id,
                     sum(revenue_amount) AS revenue_amount
              FROM fact_finance
              WHERE attribution_level = 'POSTING'
                AND posting_id IS NOT NULL AND posting_id != ''
              GROUP BY posting_id, connection_id
          )
          WHERE order_id IS NOT NULL AND order_id != ''
          GROUP BY order_id, connection_id
      ) orv ON pm.order_id = orv.order_id AND pm.connection_id = orv.connection_id
      -- Resolve seller_sku_id from canonical sales when finance row has no SKU
      LEFT JOIN (
          SELECT
              posting_id,
              connection_id,
              anyLast(seller_sku_id) AS sales_seller_sku_id
          FROM fact_sales
          WHERE posting_id IS NOT NULL AND posting_id != ''
            AND seller_sku_id IS NOT NULL
          GROUP BY posting_id, connection_id
      ) fs ON pm.posting_id = fs.posting_id AND pm.connection_id = fs.connection_id
      -- Sales quantity for COGS
      LEFT JOIN (
          SELECT posting_id, sum(quantity) AS quantity
          FROM fact_sales
          WHERE posting_id IS NOT NULL AND posting_id != ''
          GROUP BY posting_id
      ) s ON pm.posting_id = s.posting_id
      -- Product dimension for product_id resolution (uses resolved seller SKU)
      LEFT JOIN dim_product AS dp
          ON coalesce(pm.seller_sku_id, fs.sales_seller_sku_id) = dp.seller_sku_id
      -- SCD2 cost for COGS (equality-only JOIN: range filter moved to WHERE — CH hash join)
      LEFT JOIN (
          SELECT
              pm_c.posting_id AS posting_id,
              any(fpc_inner.cost_price) AS cost_price
          FROM (
              SELECT
                  posting_id,
                  connection_id,
                  any(workspace_id) AS workspace_id,
                  any(source_platform) AS source_platform,
                  any(order_id) AS order_id,
                  any(seller_sku_id) AS seller_sku_id,
                  coalesce(
                      minIf(finance_date, entry_type = 'SALE_ACCRUAL'),
                      min(finance_date)
                  ) AS finance_date,
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
                  sum(net_payout) AS net_payout
              FROM fact_finance
              WHERE attribution_level = 'POSTING'
                AND posting_id IS NOT NULL
                AND posting_id != ''
              GROUP BY posting_id, connection_id /*INCR*/
          ) AS pm_c
          INNER JOIN fact_product_cost AS fpc_inner
              ON pm_c.seller_sku_id = fpc_inner.seller_sku_id
          WHERE pm_c.finance_date >= fpc_inner.valid_from
            AND (fpc_inner.valid_to IS NULL OR pm_c.finance_date < fpc_inner.valid_to)
          GROUP BY pm_c.posting_id
      ) AS fpc_cost ON pm.posting_id = fpc_cost.posting_id
      LEFT JOIN dim_product AS p_by_id ON dp.product_id = p_by_id.product_id
      LEFT JOIN (
          SELECT
              seller_sku_id,
              anyLast(sku_code) AS sku_code,
              anyLast(marketplace_sku) AS marketplace_sku,
              anyLast(product_name) AS product_name
          FROM dim_product
          GROUP BY seller_sku_id
      ) AS p_by_sku
          ON coalesce(pm.seller_sku_id, fs.sales_seller_sku_id) = p_by_sku.seller_sku_id
      SETTINGS final = 1
      """;

  private final MaterializationJdbc jdbc;

  @Override
  public void materializeFull() {
    long ver = Instant.now().toEpochMilli();
    jdbc.fullMaterializeWithSwap(TABLE, staging -> {
      String sql = FULL_MATERIALIZE_SQL.formatted(staging, ver);
      jdbc.ch().execute(sql);
    });
    Long count = jdbc.ch().queryForObject("SELECT count() FROM " + TABLE, Long.class);
    log.info("Materialized mart_posting_pnl: rows={}", count);
  }

  @Override
  public void materializeIncremental(long jobExecutionId) {
    long ver = Instant.now().toEpochMilli();

    String affectedPostings = """
        SELECT DISTINCT posting_id
        FROM fact_finance
        WHERE job_execution_id = %d
          AND posting_id IS NOT NULL
          AND posting_id != ''""".formatted(jobExecutionId);

    String havingClause = " HAVING posting_id IN (%s)".formatted(affectedPostings);
    String incrementalSql = FULL_MATERIALIZE_SQL.formatted(TABLE, ver)
        .replace(INCR_MARKER, havingClause);

    jdbc.ch().execute(incrementalSql);
    log.info("Incremental mart_posting_pnl: jobExecutionId={}", jobExecutionId);
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
