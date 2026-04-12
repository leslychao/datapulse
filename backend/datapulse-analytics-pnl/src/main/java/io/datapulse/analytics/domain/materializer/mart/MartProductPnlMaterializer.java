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
 * <p>Three sources merged via UNION ALL, then aggregated by
 * (workspace_id, source_platform, seller_sku_id, period, attribution_level) to prevent
 * ReplacingMergeTree key collisions when the same SKU has both posting
 * and standalone (PRODUCT-level) entries:</p>
 * <ul>
 *   <li>POSTING level: rollup of mart_posting_pnl per (seller_sku_id, period)</li>
 *   <li>PRODUCT level: fact_finance entries with attribution_level = 'PRODUCT'</li>
 *   <li>ACCOUNT level: fact_finance entries with attribution_level = 'ACCOUNT'</li>
 * </ul>
 *
 * <p>COGS at product-month level uses cross-posting revenue-ratio netting (T-4/T-7).</p>
 * <p>Advertising cost: LEFT JOIN from fact_advertising via dim_product (Phase A-3).
 * Join path: fact_advertising.marketplace_sku → dim_product.seller_sku_id → base.seller_sku_id.</p>
 *
 * <p>Product labels (sku_code, product_name, marketplace_sku) are resolved here via dim_product
 * so read queries use only marts + facts.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MartProductPnlMaterializer implements AnalyticsMaterializer {

  private static final String TABLE = "mart_product_pnl";

  private static final String FULL_MATERIALIZE_SQL = """
      INSERT INTO %s
      SELECT
          base.workspace_id,
          base.connection_id,
          base.source_platform,
          base.seller_sku_id,
          base.product_id,
          base.period,
          base.attribution_level,
          base.revenue_amount,
          base.marketplace_commission_amount,
          base.acquiring_commission_amount,
          base.logistics_cost_amount,
          base.storage_cost_amount,
          base.penalties_amount,
          base.marketing_cost_amount,
          base.acceptance_cost_amount,
          base.other_marketplace_charges_amount,
          base.compensation_amount,
          base.refund_amount,
          base.net_payout,
          base.gross_cogs,
          base.product_refund_ratio,
          base.net_cogs,
          base.cogs_status,
          coalesce(ad_agg.ad_spend, toDecimal64(0, 2)) AS advertising_cost,
          base.marketplace_pnl,
          if(base.net_cogs IS NOT NULL,
             base.marketplace_pnl
                 - coalesce(ad_agg.ad_spend, toDecimal64(0, 2))
                 - base.net_cogs,
             NULL) AS full_pnl,
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
          base.ver
      FROM (
      SELECT
          connection_id,
          workspace_id,
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
          -- product_refund_ratio at product x month level (single Decimal scale for if branches)
          if(revenue_amount != 0,
             toDecimal128(abs(refund_amount) / revenue_amount, 4),
             CAST(NULL AS Nullable(Decimal(18, 4)))) AS product_refund_ratio,
          -- net_cogs = gross_cogs * (1 - product_refund_ratio)
          if(gross_cogs IS NOT NULL,
             gross_cogs * greatest(
                 toDecimal128(0, 4),
                 toDecimal128(1, 4) - coalesce(
                     if(revenue_amount != 0,
                        toDecimal128(abs(refund_amount) / revenue_amount, 4),
                        toDecimal128(0, 4)),
                     toDecimal128(0, 4))),
             NULL) AS net_cogs,
          -- cogs_status: worst of posting statuses
          cogs_status,
          -- marketplace_pnl = sum of all 11 signed measures
          revenue_amount + marketplace_commission_amount + acquiring_commission_amount
              + logistics_cost_amount + storage_cost_amount + penalties_amount
              + marketing_cost_amount + acceptance_cost_amount
              + other_marketplace_charges_amount + compensation_amount
              + refund_amount AS marketplace_pnl,
          %d AS ver
      FROM (
      SELECT
          workspace_id,
          source_platform,
          any(connection_id) AS connection_id,
          seller_sku_id,
          max(product_id) AS product_id,
          period,
          attribution_level,
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
          if(count(gross_cogs) > 0, sum(gross_cogs), NULL) AS gross_cogs,
          multiIf(
              countIf(cogs_status = 'NO_COST_PROFILE') > 0, 'NO_COST_PROFILE',
              countIf(cogs_status = 'OK') > 0, 'OK',
              'NO_SALES'
          ) AS cogs_status
      FROM (
          -- Source 1: Rollup of mart_posting_pnl → PRODUCT rows
          -- Inner query: only plain aggregates (no countIf inside multiIf/if — CH 24 ILLEGAL_AGGREGATION).
          SELECT
              connection_id,
              workspace_id,
              source_platform,
              seller_sku_id,
              product_id,
              period,
              'PRODUCT' AS attribution_level,
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
              if(cnt_no_sales > 0 AND cnt_ok = 0,
                  'NO_SALES',
                  if(cnt_no_cost_profile > 0, 'NO_COST_PROFILE', 'OK')) AS cogs_status
          FROM (
              SELECT
                  workspace_id,
                  any(connection_id) AS connection_id,
                  source_platform,
                  seller_sku_id,
                  product_id,
                  period,
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
                  countIf(cogs_status = 'NO_SALES') AS cnt_no_sales,
                  countIf(cogs_status = 'OK') AS cnt_ok,
                  countIf(cogs_status = 'NO_COST_PROFILE') AS cnt_no_cost_profile
              FROM (
                  SELECT
                      connection_id,
                      workspace_id,
                      source_platform,
                      coalesce(seller_sku_id, 0) AS seller_sku_id,
                      coalesce(product_id, 0) AS product_id,
                      toYYYYMM(finance_date) AS period,
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
                      cogs_status
                  FROM mart_posting_pnl
              ) AS posting_keys
              GROUP BY workspace_id, source_platform, seller_sku_id, product_id, period
          ) AS posting_rollup

          UNION ALL

          -- Source 2: PRODUCT-level fact_finance entries (not in mart_posting_pnl)
          -- Inner projection so GROUP BY keys match SELECT (CH 24 NOT_AN_AGGREGATE on coalesce).
          SELECT
              any(connection_id) AS connection_id,
              workspace_id,
              source_platform,
              seller_sku_id_key AS seller_sku_id,
              0 AS product_id,
              period,
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
          FROM (
              SELECT
                  connection_id,
                  workspace_id,
                  source_platform,
                  coalesce(seller_sku_id, 0) AS seller_sku_id_key,
                  toYYYYMM(finance_date) AS period,
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
                  net_payout
              FROM fact_finance
              WHERE attribution_level = 'PRODUCT'
          ) AS ff_product
          GROUP BY workspace_id, source_platform, seller_sku_id_key, period

          UNION ALL

          -- Source 3: ACCOUNT-level fact_finance entries
          -- Inner projection isolates WHERE from SELECT alias to avoid CH alias shadowing.
          SELECT
              any(connection_id) AS connection_id,
              workspace_id,
              source_platform,
              0 AS seller_sku_id,
              0 AS product_id,
              period,
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
          FROM (
              SELECT
                  connection_id,
                  workspace_id,
                  source_platform,
                  toYYYYMM(finance_date) AS period,
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
                  net_payout
              FROM fact_finance
              WHERE attribution_level = 'ACCOUNT'
          ) AS ff_account
          GROUP BY workspace_id, source_platform, period
      ) AS raw_union
      GROUP BY workspace_id, source_platform, seller_sku_id, period, attribution_level
      )
      ) AS base
      LEFT JOIN dim_product AS p_by_id ON base.product_id = p_by_id.product_id
          AND base.workspace_id = p_by_id.workspace_id
      LEFT JOIN (
          SELECT
              workspace_id,
              seller_sku_id,
              anyLast(sku_code) AS sku_code,
              anyLast(marketplace_sku) AS marketplace_sku,
              anyLast(product_name) AS product_name
          FROM dim_product
          GROUP BY workspace_id, seller_sku_id
      ) AS p_by_sku
          ON base.seller_sku_id = p_by_sku.seller_sku_id
          AND base.workspace_id = p_by_sku.workspace_id
      LEFT JOIN (
          SELECT
              fa.workspace_id,
              fa.source_platform,
              dp.seller_sku_id,
              toYYYYMM(fa.ad_date) AS period,
              sum(fa.spend) AS ad_spend
          FROM fact_advertising AS fa
          INNER JOIN dim_product AS dp
              ON fa.marketplace_sku = dp.marketplace_sku
              AND fa.workspace_id = dp.workspace_id
          GROUP BY fa.workspace_id, fa.source_platform, dp.seller_sku_id, period
      ) AS ad_agg
          ON base.workspace_id = ad_agg.workspace_id
          AND base.source_platform = ad_agg.source_platform
          AND base.seller_sku_id = ad_agg.seller_sku_id
          AND base.period = ad_agg.period
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
    log.info("Materialized mart_product_pnl: rows={}", count);
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

  @Override
  public int order() {
    return 1;
  }
}
