package io.datapulse.analytics.persistence;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdvertisingCampaignReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final Map<String, String> SORT_WHITELIST = Map.of(
      "name", "cac.name",
      "campaignType", "cac.campaign_type",
      "status", "cac.status",
      "dailyBudget", "cac.daily_budget",
      "sourcePlatform", "mc.marketplace_type"
  );

  private static final String BASE_SELECT = """
      SELECT cac.id,
             cac.connection_id,
             cac.external_campaign_id,
             cac.name,
             cac.campaign_type,
             cac.status,
             cac.placement,
             cac.daily_budget,
             mc.marketplace_type AS source_platform
      FROM canonical_advertising_campaign cac
      JOIN marketplace_connection mc ON mc.id = cac.connection_id
      """;

  private static final String BASE_COUNT = """
      SELECT count(*)
      FROM canonical_advertising_campaign cac
      JOIN marketplace_connection mc ON mc.id = cac.connection_id
      """;

  public List<CampaignPgRow> findCampaigns(
      long workspaceId, String sourcePlatform, String status,
      String sortColumn, int limit, long offset) {

    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder(BASE_SELECT);
    sb.append(" WHERE mc.workspace_id = :workspaceId");
    appendSourcePlatformFilter(sb, params, sourcePlatform);
    appendStatusFilter(sb, params, status);

    String orderBy = SORT_WHITELIST.getOrDefault(sortColumn, "cac.name");
    sb.append(" ORDER BY ").append(orderBy).append(" ASC NULLS LAST");
    sb.append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit);
    params.addValue("offset", offset);

    return jdbc.query(sb.toString(), params, (rs, rowNum) -> new CampaignPgRow(
        rs.getLong("id"),
        rs.getLong("connection_id"),
        rs.getString("external_campaign_id"),
        rs.getString("name"),
        rs.getString("campaign_type"),
        rs.getString("status"),
        rs.getString("placement"),
        rs.getBigDecimal("daily_budget"),
        rs.getString("source_platform")
    ));
  }

  public long countCampaigns(long workspaceId, String sourcePlatform,
      String status) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder(BASE_COUNT);
    sb.append(" WHERE mc.workspace_id = :workspaceId");
    appendSourcePlatformFilter(sb, params, sourcePlatform);
    appendStatusFilter(sb, params, status);

    Long result = jdbc.queryForObject(sb.toString(), params, Long.class);
    return result != null ? result : 0L;
  }

  private void appendSourcePlatformFilter(StringBuilder sb,
      MapSqlParameterSource params, String sourcePlatform) {
    if (sourcePlatform != null && !sourcePlatform.isBlank()) {
      sb.append(" AND mc.marketplace_type = :sourcePlatform");
      params.addValue("sourcePlatform", sourcePlatform.trim());
    }
  }

  private void appendStatusFilter(StringBuilder sb, MapSqlParameterSource params,
      String status) {
    if (status != null && !status.isBlank()) {
      sb.append(" AND cac.status = :status");
      params.addValue("status", status);
    }
  }
}
