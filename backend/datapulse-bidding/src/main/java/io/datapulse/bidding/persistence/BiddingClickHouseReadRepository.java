package io.datapulse.bidding.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BiddingClickHouseReadRepository {

  private final NamedParameterJdbcTemplate ch;

  public BiddingClickHouseReadRepository(
      @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
    this.ch = new NamedParameterJdbcTemplate(clickhouseJdbcTemplate);
  }

  // ── advertising metrics (mart_advertising_product) ──────────────────────

  private static final String ADVERTISING_METRICS = """
      SELECT
          avg(map.drr_pct) AS drr_pct,
          avg(map.cpo) AS cpo_pct,
          avg(map.roas) AS roas,
          sum(map.spend) AS total_spend,
          sum(map.impressions) AS impressions,
          sum(map.clicks) AS clicks,
          sum(map.ad_orders) AS ad_orders
      FROM mart_advertising_product AS map
      WHERE map.workspace_id = :workspaceId
        AND map.marketplace_sku = :marketplaceSku
        AND map.period >= toYYYYMM(today() - :lookbackDays)
      SETTINGS final = 1
      """;

  public Optional<AdvertisingMetricsRow> findAdvertisingMetrics(
      long workspaceId, String marketplaceSku, int lookbackDays) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("marketplaceSku", marketplaceSku)
        .addValue("lookbackDays", lookbackDays);

    List<AdvertisingMetricsRow> rows = ch.query(ADVERTISING_METRICS, params,
        (rs, rowNum) -> new AdvertisingMetricsRow(
            rs.getBigDecimal("drr_pct"),
            rs.getBigDecimal("cpo_pct"),
            rs.getBigDecimal("roas"),
            rs.getBigDecimal("total_spend"),
            rs.getLong("impressions"),
            rs.getLong("clicks"),
            rs.getLong("ad_orders")));

    return rows.stream()
        .filter(r -> r.totalSpend() != null
            && r.totalSpend().compareTo(BigDecimal.ZERO) > 0)
        .findFirst();
  }

  // ── margin metrics (mart_product_pnl via dim_product) ──────────────────

  private static final String MARGIN_METRICS = """
      SELECT
          avg(
              if(m.revenue_amount > 0,
                 m.full_pnl / m.revenue_amount * 100,
                 NULL)
          ) AS margin_pct
      FROM mart_product_pnl AS m
      INNER JOIN dim_product AS dp
          ON m.seller_sku_id = dp.seller_sku_id
      WHERE dp.marketplace_sku = :marketplaceSku
        AND m.workspace_id = :workspaceId
        AND m.period >= toYYYYMM(today() - :lookbackDays)
      SETTINGS final = 1
      """;

  public Optional<MarginMetricsRow> findMarginMetrics(
      long workspaceId, String marketplaceSku, int lookbackDays) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("marketplaceSku", marketplaceSku)
        .addValue("lookbackDays", lookbackDays);

    List<MarginMetricsRow> rows = ch.query(MARGIN_METRICS, params,
        (rs, rowNum) -> new MarginMetricsRow(
            rs.getBigDecimal("margin_pct")));

    return rows.stream()
        .filter(r -> r.marginPct() != null)
        .findFirst();
  }

  // ── stock metrics (mart_inventory_analysis via dim_product) ────────────

  private static final String STOCK_METRICS = """
      SELECT
          toInt32(avg(m.days_of_cover)) AS stock_days
      FROM (
          SELECT
              product_id,
              warehouse_id,
              argMax(days_of_cover, ver) AS days_of_cover
          FROM mart_inventory_analysis FINAL
          WHERE workspace_id = :workspaceId
          GROUP BY product_id, warehouse_id
      ) AS m
      INNER JOIN dim_product AS dp
          ON m.product_id = dp.product_id
      WHERE dp.marketplace_sku = :marketplaceSku
      """;

  public Optional<StockMetricsRow> findStockMetrics(
      long workspaceId, String marketplaceSku) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("marketplaceSku", marketplaceSku);

    List<StockMetricsRow> rows = ch.query(STOCK_METRICS, params,
        (rs, rowNum) -> new StockMetricsRow(
            rs.getInt("stock_days")));

    return rows.stream()
        .filter(r -> r.stockDays() > 0)
        .findFirst();
  }
}
