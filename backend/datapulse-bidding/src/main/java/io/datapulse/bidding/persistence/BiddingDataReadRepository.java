package io.datapulse.bidding.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BiddingDataReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String ACTIVE_ASSIGNMENT = """
      SELECT
          bpa.id,
          bpa.bid_policy_id,
          bp.strategy_type,
          bp.execution_mode,
          bp.config
      FROM bid_policy_assignment bpa
      JOIN bid_policy bp ON bp.id = bpa.bid_policy_id
      WHERE bpa.marketplace_offer_id = :marketplaceOfferId
        AND bp.status = 'ACTIVE'
      LIMIT 1
      """;

  private static final String ACTIVE_LOCK = """
      SELECT id, locked_bid, reason, expires_at
      FROM manual_bid_lock
      WHERE workspace_id = :workspaceId
        AND marketplace_offer_id = :marketplaceOfferId
        AND (expires_at IS NULL OR expires_at > now())
      ORDER BY created_at DESC
      LIMIT 1
      """;

  private static final String LAST_DECISION = """
      SELECT id, decision_type, target_bid, created_at
      FROM bid_decision
      WHERE workspace_id = :workspaceId
        AND marketplace_offer_id = :marketplaceOfferId
      ORDER BY created_at DESC
      LIMIT 1
      """;

  private static final String ELIGIBLE_PRODUCTS = """
      SELECT
          bpa.marketplace_offer_id,
          mo.marketplace_sku,
          mo.marketplace_connection_id
      FROM bid_policy_assignment bpa
      JOIN marketplace_offer mo ON mo.id = bpa.marketplace_offer_id
      WHERE bpa.bid_policy_id = :bidPolicyId
        AND bpa.workspace_id = :workspaceId
        AND bpa.marketplace_offer_id IS NOT NULL
      """;

  private static final String CAMPAIGN_INFO = """
      SELECT
          cac.external_campaign_id,
          cac.status,
          cac.source_platform
      FROM canonical_advertising_campaign cac
      WHERE cac.connection_id = (
          SELECT mo.marketplace_connection_id
          FROM marketplace_offer mo
          WHERE mo.id = :marketplaceOfferId
      )
        AND cac.status IN ('ACTIVE', 'SUSPENDED', 'PAUSED')
      ORDER BY cac.updated_at DESC
      LIMIT 1
      """;

  public Optional<BidPolicyAssignmentRow> findActiveAssignment(long marketplaceOfferId) {
    var rows = jdbc.query(ACTIVE_ASSIGNMENT,
        Map.of("marketplaceOfferId", marketplaceOfferId),
        (rs, rowNum) -> new BidPolicyAssignmentRow(
            rs.getLong("id"),
            rs.getLong("bid_policy_id"),
            rs.getString("strategy_type"),
            rs.getString("execution_mode"),
            rs.getString("config")));
    return rows.stream().findFirst();
  }

  public Optional<ManualBidLockRow> findActiveLock(
      long workspaceId, long marketplaceOfferId) {
    var rows = jdbc.query(ACTIVE_LOCK,
        Map.of("workspaceId", workspaceId,
            "marketplaceOfferId", marketplaceOfferId),
        (rs, rowNum) -> new ManualBidLockRow(
            rs.getLong("id"),
            rs.getObject("locked_bid", Integer.class),
            rs.getString("reason"),
            toInstant(rs.getObject("expires_at", OffsetDateTime.class))));
    return rows.stream().findFirst();
  }

  public Optional<BidDecisionRow> findLastDecision(
      long workspaceId, long marketplaceOfferId) {
    var rows = jdbc.query(LAST_DECISION,
        Map.of("workspaceId", workspaceId,
            "marketplaceOfferId", marketplaceOfferId),
        (rs, rowNum) -> new BidDecisionRow(
            rs.getLong("id"),
            rs.getString("decision_type"),
            rs.getObject("target_bid", Integer.class),
            toInstant(rs.getObject("created_at", OffsetDateTime.class))));
    return rows.stream().findFirst();
  }

  public List<EligibleProductRow> findEligibleProducts(
      long workspaceId, long bidPolicyId) {
    return jdbc.query(ELIGIBLE_PRODUCTS,
        Map.of("workspaceId", workspaceId,
            "bidPolicyId", bidPolicyId),
        (rs, rowNum) -> new EligibleProductRow(
            rs.getLong("marketplace_offer_id"),
            rs.getString("marketplace_sku"),
            rs.getLong("marketplace_connection_id")));
  }

  public Optional<CampaignInfoRow> findCampaignInfo(long marketplaceOfferId) {
    var rows = jdbc.query(CAMPAIGN_INFO,
        Map.of("marketplaceOfferId", marketplaceOfferId),
        (rs, rowNum) -> new CampaignInfoRow(
            rs.getString("external_campaign_id"),
            rs.getString("status"),
            rs.getString("source_platform")));
    return rows.stream().findFirst();
  }

  private static Instant toInstant(OffsetDateTime odt) {
    return odt != null ? odt.toInstant() : null;
  }
}
