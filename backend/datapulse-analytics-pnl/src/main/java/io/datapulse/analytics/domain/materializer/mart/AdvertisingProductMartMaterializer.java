package io.datapulse.analytics.domain.materializer.mart;

import java.time.Instant;

import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Materializes mart_advertising_product from fact_advertising + fact_finance.
 *
 * <p>Aggregates ad metrics per (connection_id, source_platform, marketplace_sku, period):
 * spend, impressions, clicks, ad_orders, ad_revenue, total_revenue,
 * and derived ratios DRR, CPO, ROAS, CPC, CTR, CR.</p>
 *
 * <p>total_revenue is joined from fact_finance via dim_product
 * (marketplace_sku → seller_sku_id) to compute DRR.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvertisingProductMartMaterializer implements AnalyticsMaterializer {

  private static final String TABLE = "mart_advertising_product";

  private static final String FULL_MATERIALIZE_SQL = """
      INSERT INTO %s
      SELECT
          fa.workspace_id,
          fa.connection_id,
          fa.source_platform,
          fa.marketplace_sku,
          toYYYYMM(fa.ad_date) AS period,
          sum(fa.spend) AS spend,
          sum(fa.views) AS impressions,
          sum(fa.clicks) AS clicks,
          sum(fa.orders) AS ad_orders,
          sum(fa.ordered_revenue) AS ad_revenue,
          coalesce(ff_rev.total_revenue, toDecimal64(0, 2)) AS total_revenue,
          if(ff_rev.total_revenue > 0,
             sum(fa.spend) / ff_rev.total_revenue * 100, NULL) AS drr_pct,
          if(sum(fa.orders) > 0,
             sum(fa.spend) / sum(fa.orders), NULL) AS cpo,
          if(sum(fa.spend) > 0,
             sum(fa.ordered_revenue) / sum(fa.spend), NULL) AS roas,
          if(sum(fa.clicks) > 0,
             sum(fa.spend) / sum(fa.clicks), NULL) AS cpc,
          if(sum(fa.views) > 0,
             sum(fa.clicks) / sum(fa.views) * 100, NULL) AS ctr_pct,
          if(sum(fa.clicks) > 0,
             sum(fa.orders) / sum(fa.clicks) * 100, NULL) AS cr_pct,
          %d AS ver
      FROM fact_advertising AS fa
      LEFT JOIN (
          SELECT
              dp.connection_id AS connection_id,
              dp.marketplace_sku AS marketplace_sku,
              toYYYYMM(ff.finance_date) AS period,
              sum(ff.revenue_amount) AS total_revenue
          FROM fact_finance AS ff
          INNER JOIN dim_product AS dp
              ON ff.connection_id = dp.connection_id
              AND ff.seller_sku_id = dp.seller_sku_id
          WHERE ff.attribution_level IN ('POSTING', 'PRODUCT')
          GROUP BY connection_id, marketplace_sku, period
      ) AS ff_rev
          ON fa.connection_id = ff_rev.connection_id
          AND fa.marketplace_sku = ff_rev.marketplace_sku
          AND toYYYYMM(fa.ad_date) = ff_rev.period
      GROUP BY
          fa.workspace_id, fa.connection_id, fa.source_platform, fa.marketplace_sku,
          period, ff_rev.total_revenue
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
    log.info("Materialized mart_advertising_product: rows={}", count);
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
    return 2;
  }
}
