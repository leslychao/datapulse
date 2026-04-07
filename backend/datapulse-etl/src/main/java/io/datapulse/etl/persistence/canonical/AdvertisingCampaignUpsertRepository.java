package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdvertisingCampaignUpsertRepository {

  private final JdbcTemplate jdbc;

  private static final int DEFAULT_BATCH_SIZE = 500;

  private static final String UPSERT = """
      INSERT INTO canonical_advertising_campaign (workspace_id, connection_id, source_platform,
                                                  external_campaign_id, name, campaign_type,
                                                  status, placement, daily_budget,
                                                  start_time, end_time,
                                                  created_at_external, synced_at,
                                                  created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
      ON CONFLICT (workspace_id, source_platform, external_campaign_id) DO UPDATE SET
          connection_id = EXCLUDED.connection_id,
          name = EXCLUDED.name,
          campaign_type = EXCLUDED.campaign_type,
          status = EXCLUDED.status,
          placement = EXCLUDED.placement,
          daily_budget = EXCLUDED.daily_budget,
          start_time = EXCLUDED.start_time,
          end_time = EXCLUDED.end_time,
          created_at_external = EXCLUDED.created_at_external,
          synced_at = EXCLUDED.synced_at,
          updated_at = now()
      WHERE (canonical_advertising_campaign.name,
             canonical_advertising_campaign.campaign_type,
             canonical_advertising_campaign.status,
             canonical_advertising_campaign.placement,
             canonical_advertising_campaign.daily_budget,
             canonical_advertising_campaign.start_time,
             canonical_advertising_campaign.end_time,
             canonical_advertising_campaign.created_at_external)
          IS DISTINCT FROM
            (EXCLUDED.name,
             EXCLUDED.campaign_type,
             EXCLUDED.status,
             EXCLUDED.placement,
             EXCLUDED.daily_budget,
             EXCLUDED.start_time,
             EXCLUDED.end_time,
             EXCLUDED.created_at_external)
      """;

  private static final String MARK_STALE = """
      UPDATE canonical_advertising_campaign
      SET status = 'archived', updated_at = now()
      WHERE connection_id = ?
        AND status IN ('active', 'on_pause')
        AND synced_at < now() - CAST(? AS interval)
      RETURNING id
      """;

  private static final String FIND_ACTIVE_CAMPAIGN_IDS = """
      SELECT CAST(external_campaign_id AS bigint)
      FROM canonical_advertising_campaign
      WHERE connection_id = ?
        AND status IN ('active', 'on_pause', 'ready', 'moderation')
      """;

  public void upsertAll(List<CanonicalAdvertisingCampaignEntity> entities) {
    jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
        (ps, e) -> {
          ps.setLong(1, e.getWorkspaceId());
          ps.setLong(2, e.getConnectionId());
          ps.setString(3, e.getSourcePlatform());
          ps.setString(4, e.getExternalCampaignId());
          ps.setString(5, e.getName());
          ps.setString(6, e.getCampaignType());
          ps.setString(7, e.getStatus());
          ps.setString(8, e.getPlacement());
          ps.setBigDecimal(9, e.getDailyBudget());
          ps.setObject(10, e.getStartTime() != null
              ? Timestamp.from(e.getStartTime().toInstant()) : null);
          ps.setObject(11, e.getEndTime() != null
              ? Timestamp.from(e.getEndTime().toInstant()) : null);
          ps.setObject(12, e.getCreatedAtExternal() != null
              ? Timestamp.from(e.getCreatedAtExternal().toInstant()) : null);
          ps.setObject(13, e.getSyncedAt() != null
              ? Timestamp.from(e.getSyncedAt().toInstant()) : null);
        });
  }

  public List<Long> markStaleCampaigns(long connectionId, Duration threshold) {
    String interval = "%d seconds".formatted(threshold.toSeconds());
    return jdbc.queryForList(MARK_STALE, Long.class, connectionId, interval);
  }

  public List<Long> findActiveCampaignIds(long connectionId) {
    return jdbc.queryForList(FIND_ACTIVE_CAMPAIGN_IDS, Long.class, connectionId);
  }
}
