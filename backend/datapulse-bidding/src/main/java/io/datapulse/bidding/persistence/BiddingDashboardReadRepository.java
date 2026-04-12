package io.datapulse.bidding.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.datapulse.bidding.api.BiddingDashboardResponse.TopProductItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BiddingDashboardReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String COUNT_MANAGED_PRODUCTS = """
      SELECT COUNT(DISTINCT bpa.marketplace_offer_id)
      FROM bid_policy_assignment bpa
      JOIN bid_policy bp ON bp.id = bpa.bid_policy_id
      WHERE bp.workspace_id = :workspaceId
        AND bp.status = 'ACTIVE'
      """;

  private static final String COUNT_ACTIVE_POLICIES = """
      SELECT COUNT(*)
      FROM bid_policy
      WHERE workspace_id = :workspaceId
        AND status = 'ACTIVE'
      """;

  private static final String PRODUCTS_BY_STRATEGY = """
      SELECT bp.strategy_type, COUNT(DISTINCT bpa.marketplace_offer_id) AS cnt
      FROM bid_policy_assignment bpa
      JOIN bid_policy bp ON bp.id = bpa.bid_policy_id
      WHERE bp.workspace_id = :workspaceId
        AND bp.status = 'ACTIVE'
      GROUP BY bp.strategy_type
      """;

  private static final String DECISIONS_BY_TYPE = """
      SELECT bd.decision_type, COUNT(*) AS cnt
      FROM bid_decision bd
      WHERE bd.workspace_id = :workspaceId
        AND bd.created_at >= now() - interval '7 days'
      GROUP BY bd.decision_type
      """;

  private static final String RUN_STATS = """
      SELECT
          COUNT(*) AS total,
          COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
          COUNT(*) FILTER (WHERE status = 'PAUSED') AS paused
      FROM bidding_run
      WHERE workspace_id = :workspaceId
        AND created_at >= now() - interval '7 days'
      """;

  private static final String TOP_HIGH_DRR = """
      SELECT
          bd.marketplace_offer_id,
          mo.marketplace_sku,
          bp.strategy_type,
          bd.decision_type AS last_decision_type,
          bd.current_bid,
          bd.drr_pct
      FROM bid_decision bd
      JOIN (
          SELECT marketplace_offer_id, MAX(id) AS max_id
          FROM bid_decision
          WHERE workspace_id = :workspaceId
            AND created_at >= now() - interval '7 days'
          GROUP BY marketplace_offer_id
      ) latest ON bd.id = latest.max_id
      JOIN marketplace_offer mo ON mo.id = bd.marketplace_offer_id
      JOIN bid_policy bp ON bp.id = bd.bid_policy_id
      WHERE bd.drr_pct IS NOT NULL
      ORDER BY bd.drr_pct DESC
      LIMIT :limit
      """;

  private static final String TOP_IMPROVED = """
      SELECT
          bd.marketplace_offer_id,
          mo.marketplace_sku,
          bp.strategy_type,
          bd.decision_type AS last_decision_type,
          bd.current_bid,
          bd.drr_pct
      FROM bid_decision bd
      JOIN (
          SELECT marketplace_offer_id, MAX(id) AS max_id
          FROM bid_decision
          WHERE workspace_id = :workspaceId
            AND created_at >= now() - interval '7 days'
            AND decision_type = 'BID_DOWN'
          GROUP BY marketplace_offer_id
      ) latest ON bd.id = latest.max_id
      JOIN marketplace_offer mo ON mo.id = bd.marketplace_offer_id
      JOIN bid_policy bp ON bp.id = bd.bid_policy_id
      ORDER BY bd.drr_pct ASC NULLS LAST
      LIMIT :limit
      """;

  public int countManagedProducts(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    Integer count = jdbc.queryForObject(COUNT_MANAGED_PRODUCTS, params, Integer.class);
    return count != null ? count : 0;
  }

  public int countActivePolicies(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    Integer count = jdbc.queryForObject(COUNT_ACTIVE_POLICIES, params, Integer.class);
    return count != null ? count : 0;
  }

  public Map<String, Integer> countProductsByStrategy(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    Map<String, Integer> result = new HashMap<>();
    jdbc.query(PRODUCTS_BY_STRATEGY, params, (rs, rowNum) -> {
      result.put(rs.getString("strategy_type"), rs.getInt("cnt"));
      return null;
    });
    return result;
  }

  public Map<String, Integer> countDecisionsByType(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    Map<String, Integer> result = new HashMap<>();
    jdbc.query(DECISIONS_BY_TYPE, params, (rs, rowNum) -> {
      result.put(rs.getString("decision_type"), rs.getInt("cnt"));
      return null;
    });
    return result;
  }

  public RunStats runStatsLast7Days(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.queryForObject(RUN_STATS, params, (rs, rowNum) ->
        new RunStats(
            rs.getInt("total"),
            rs.getInt("failed"),
            rs.getInt("paused")));
  }

  public List<TopProductItem> topHighDrrProducts(long workspaceId, int limit) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("limit", limit);
    return jdbc.query(TOP_HIGH_DRR, params, (rs, rowNum) ->
        new TopProductItem(
            rs.getLong("marketplace_offer_id"),
            rs.getString("marketplace_sku"),
            rs.getString("strategy_type"),
            rs.getString("last_decision_type"),
            rs.getObject("current_bid", Integer.class),
            rs.getBigDecimal("drr_pct")));
  }

  public List<TopProductItem> topImprovedProducts(long workspaceId, int limit) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("limit", limit);
    return jdbc.query(TOP_IMPROVED, params, (rs, rowNum) ->
        new TopProductItem(
            rs.getLong("marketplace_offer_id"),
            rs.getString("marketplace_sku"),
            rs.getString("strategy_type"),
            rs.getString("last_decision_type"),
            rs.getObject("current_bid", Integer.class),
            rs.getBigDecimal("drr_pct")));
  }

  public record RunStats(int total, int failed, int paused) {}
}
