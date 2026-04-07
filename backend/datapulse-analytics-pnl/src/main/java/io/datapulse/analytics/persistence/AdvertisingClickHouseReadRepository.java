package io.datapulse.analytics.persistence;

import io.datapulse.analytics.config.ClickHouseReadJdbc;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdvertisingClickHouseReadRepository {

  private final ClickHouseReadJdbc jdbc;

  private static final int RECOMMENDATION_LOOKBACK_DAYS = 30;

  private static final String CAMPAIGN_METRICS_SQL = """
      SELECT
          connection_id,
          campaign_id,
          sumIf(spend, ad_date >= :currentFrom AND ad_date < :currentTo) AS current_spend,
          toInt32(sumIf(orders, ad_date >= :currentFrom AND ad_date < :currentTo)) AS current_orders,
          sumIf(ordered_revenue, ad_date >= :currentFrom AND ad_date < :currentTo) AS current_revenue,
          sumIf(spend, ad_date >= :prevFrom AND ad_date < :prevTo) AS prev_spend,
          sumIf(ordered_revenue, ad_date >= :prevFrom AND ad_date < :prevTo) AS prev_revenue
      FROM fact_advertising
      WHERE workspace_id = :workspaceId
        AND campaign_id IN (:campaignIds)
        AND ad_date >= :prevFrom
        AND ad_date < :currentTo
      GROUP BY connection_id, campaign_id
      SETTINGS final = 1
      """;

  public Map<String, CampaignMetrics> findCampaignMetrics(
      long workspaceId, List<Long> campaignIds, int periodDays) {

    if (campaignIds.isEmpty()) {
      return Collections.emptyMap();
    }

    LocalDate today = LocalDate.now();
    LocalDate currentTo = today;
    LocalDate currentFrom = today.minusDays(periodDays);
    LocalDate prevTo = currentFrom;
    LocalDate prevFrom = currentFrom.minusDays(periodDays);

    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("campaignIds", campaignIds)
        .addValue("currentFrom", currentFrom)
        .addValue("currentTo", currentTo)
        .addValue("prevFrom", prevFrom)
        .addValue("prevTo", prevTo);

    List<CampaignMetrics> metrics = jdbc.ch().query(
        CAMPAIGN_METRICS_SQL, params, (rs, rowNum) -> new CampaignMetrics(
            rs.getLong("connection_id"),
            rs.getLong("campaign_id"),
            rs.getBigDecimal("current_spend"),
            rs.getInt("current_orders"),
            rs.getBigDecimal("current_revenue"),
            rs.getBigDecimal("prev_spend"),
            rs.getBigDecimal("prev_revenue")
        ));

    return metrics.stream()
        .collect(Collectors.toMap(
            m -> m.connectionId() + ":" + m.campaignId(),
            m -> m));
  }

  // --- Recommendation queries (Phase C) ---

  private static final String OFFER_AD_METRICS_SQL = """
      SELECT
          dp.product_id AS offer_id,
          sum(fa.spend) AS spend_30d,
          sum(fa.clicks) AS clicks_30d,
          sum(fa.orders) AS orders_30d,
          sum(fa.ordered_revenue) AS revenue_30d,
          toInt32(count(DISTINCT fa.ad_date)) AS ad_days,
          if(sum(fa.clicks) > 0,
             sum(fa.spend) / sum(fa.clicks), NULL) AS avg_cpc,
          if(sum(fa.clicks) > 0,
             sum(fa.orders) / sum(fa.clicks), NULL) AS avg_cr,
          if(sum(fa.ordered_revenue) > 0,
             sum(fa.spend) / sum(fa.ordered_revenue) * 100, NULL) AS current_drr_pct
      FROM fact_advertising AS fa
      INNER JOIN dim_product AS dp
          ON fa.marketplace_sku = dp.marketplace_sku
      WHERE dp.product_id IN (:offerIds)
        AND fa.ad_date >= today() - :lookback
      GROUP BY dp.product_id
      SETTINGS final = 1
      """;

  public Map<Long, OfferAdMetrics> findOfferAdMetrics(List<Long> offerIds) {
    if (offerIds.isEmpty()) {
      return Collections.emptyMap();
    }

    var params = new MapSqlParameterSource()
        .addValue("offerIds", offerIds)
        .addValue("lookback", RECOMMENDATION_LOOKBACK_DAYS);

    List<OfferAdMetrics> rows = jdbc.ch().query(
        OFFER_AD_METRICS_SQL, params, (rs, rowNum) -> new OfferAdMetrics(
            rs.getLong("offer_id"),
            rs.getBigDecimal("spend_30d"),
            rs.getLong("clicks_30d"),
            rs.getLong("orders_30d"),
            rs.getBigDecimal("revenue_30d"),
            rs.getInt("ad_days"),
            rs.getBigDecimal("avg_cpc"),
            rs.getBigDecimal("avg_cr"),
            rs.getBigDecimal("current_drr_pct")
        ));

    return rows.stream()
        .collect(Collectors.toMap(OfferAdMetrics::offerId, Function.identity()));
  }

  private static final String CATEGORY_AVG_METRICS_SQL = """
      SELECT
          dp.category,
          if(sum(fa.clicks) > 0,
             sum(fa.spend) / sum(fa.clicks), NULL) AS avg_cpc,
          if(sum(fa.clicks) > 0,
             toDecimal64(sum(fa.orders), 6) / sum(fa.clicks), NULL) AS avg_cr
      FROM fact_advertising AS fa
      INNER JOIN dim_product AS dp
          ON fa.marketplace_sku = dp.marketplace_sku
      WHERE fa.workspace_id = :workspaceId
        AND dp.category IN (:categories)
        AND fa.ad_date >= today() - :lookback
      GROUP BY dp.category
      HAVING sum(fa.clicks) >= 10
      SETTINGS final = 1
      """;

  public Map<String, CategoryAdAvg> findCategoryAvgMetrics(
      long workspaceId, List<String> categories) {

    if (categories.isEmpty()) {
      return Collections.emptyMap();
    }

    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("categories", categories)
        .addValue("lookback", RECOMMENDATION_LOOKBACK_DAYS);

    List<CategoryAdAvg> rows = jdbc.ch().query(
        CATEGORY_AVG_METRICS_SQL, params, (rs, rowNum) -> new CategoryAdAvg(
            rs.getString("category"),
            rs.getBigDecimal("avg_cpc"),
            rs.getBigDecimal("avg_cr")
        ));

    return rows.stream()
        .collect(Collectors.toMap(CategoryAdAvg::category, Function.identity()));
  }

  private static final String CROSS_MP_COMPARISON_SQL = """
      SELECT
          dp.seller_sku_id,
          map.source_platform,
          map.spend,
          map.drr_pct,
          map.roas,
          map.cpo,
          map.cpc,
          map.cr_pct
      FROM mart_advertising_product AS map
      INNER JOIN dim_product AS dp
          ON map.marketplace_sku = dp.marketplace_sku
      WHERE dp.seller_sku_id IN (:sellerSkuIds)
        AND map.workspace_id = :workspaceId
      ORDER BY dp.seller_sku_id, map.source_platform
      SETTINGS final = 1
      """;

  public List<CrossMpAdComparison> findCrossMarketplaceComparison(
      long workspaceId, List<Long> sellerSkuIds) {

    if (sellerSkuIds.isEmpty()) {
      return Collections.emptyList();
    }

    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("sellerSkuIds", sellerSkuIds);

    return jdbc.ch().query(
        CROSS_MP_COMPARISON_SQL, params, (rs, rowNum) -> new CrossMpAdComparison(
            rs.getLong("seller_sku_id"),
            rs.getString("source_platform"),
            rs.getBigDecimal("spend"),
            rs.getBigDecimal("drr_pct"),
            rs.getBigDecimal("roas"),
            rs.getBigDecimal("cpo"),
            rs.getBigDecimal("cpc"),
            rs.getBigDecimal("cr_pct")
        ));
  }
}
