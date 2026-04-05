package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.datapulse.analytics.config.ClickHouseReadJdbc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdvertisingClickHouseReadRepository {

  private final ClickHouseReadJdbc jdbc;

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
      WHERE connection_id IN (:connectionIds)
        AND campaign_id IN (:campaignIds)
        AND ad_date >= :prevFrom
        AND ad_date < :currentTo
      GROUP BY connection_id, campaign_id
      SETTINGS final = 1
      """;

  /**
   * Enrichment query: fetches aggregated metrics for displayed campaigns.
   * Returns a map keyed by "connectionId:campaignId" for fast lookup.
   */
  public Map<String, CampaignMetrics> findCampaignMetrics(
      List<Long> connectionIds, List<Long> campaignIds, int periodDays) {

    if (connectionIds.isEmpty() || campaignIds.isEmpty()) {
      return Collections.emptyMap();
    }

    LocalDate today = LocalDate.now();
    LocalDate currentTo = today;
    LocalDate currentFrom = today.minusDays(periodDays);
    LocalDate prevTo = currentFrom;
    LocalDate prevFrom = currentFrom.minusDays(periodDays);

    var params = new MapSqlParameterSource()
        .addValue("connectionIds", connectionIds)
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
}
