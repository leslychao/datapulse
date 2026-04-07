package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Batch upsert for canonical_promo_campaign table.
 *
 * <p>Used by {@code WbPromoSyncSource} and {@code OzonPromoSyncSource}
 * during PROMO_SYNC event processing.</p>
 */
@Repository
@RequiredArgsConstructor
public class CanonicalPromoCampaignUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_promo_campaign (workspace_id, connection_id, external_promo_id,
                                                  source_platform, promo_name, promo_type, status,
                                                  date_from, date_to, freeze_at, participation_deadline,
                                                  description, mechanic, is_participating,
                                                  raw_payload, job_execution_id, synced_at,
                                                  created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, now(), now())
            ON CONFLICT (workspace_id, source_platform, external_promo_id) DO UPDATE SET
                connection_id = EXCLUDED.connection_id,
                promo_name = EXCLUDED.promo_name,
                promo_type = EXCLUDED.promo_type,
                status = EXCLUDED.status,
                date_from = EXCLUDED.date_from,
                date_to = EXCLUDED.date_to,
                freeze_at = EXCLUDED.freeze_at,
                participation_deadline = EXCLUDED.participation_deadline,
                description = EXCLUDED.description,
                mechanic = EXCLUDED.mechanic,
                is_participating = EXCLUDED.is_participating,
                raw_payload = EXCLUDED.raw_payload,
                job_execution_id = EXCLUDED.job_execution_id,
                synced_at = EXCLUDED.synced_at,
                updated_at = now()
            WHERE (canonical_promo_campaign.promo_name, canonical_promo_campaign.promo_type,
                   canonical_promo_campaign.status, canonical_promo_campaign.date_from,
                   canonical_promo_campaign.date_to, canonical_promo_campaign.freeze_at,
                   canonical_promo_campaign.participation_deadline, canonical_promo_campaign.description,
                   canonical_promo_campaign.mechanic, canonical_promo_campaign.is_participating)
                IS DISTINCT FROM
                  (EXCLUDED.promo_name, EXCLUDED.promo_type,
                   EXCLUDED.status, EXCLUDED.date_from,
                   EXCLUDED.date_to, EXCLUDED.freeze_at,
                   EXCLUDED.participation_deadline, EXCLUDED.description,
                   EXCLUDED.mechanic, EXCLUDED.is_participating)
            """;

    private static final String MARK_STALE = """
            UPDATE canonical_promo_campaign
            SET status = 'ENDED', updated_at = NOW()
            WHERE connection_id = ?
              AND status IN ('UPCOMING', 'ACTIVE')
              AND synced_at < NOW() - CAST(? AS interval)
            RETURNING id
            """;

    public List<Long> markStaleCampaigns(long connectionId, Duration threshold) {
        String interval = "%d seconds".formatted(threshold.toSeconds());
        return jdbc.queryForList(MARK_STALE, Long.class, connectionId, interval);
    }

    public void batchUpsert(List<CanonicalPromoCampaignEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setLong(2, e.getConnectionId());
                    ps.setString(3, e.getExternalPromoId());
                    ps.setString(4, e.getSourcePlatform());
                    ps.setString(5, e.getPromoName());
                    ps.setString(6, e.getPromoType());
                    ps.setString(7, e.getStatus());
                    ps.setObject(8, e.getDateFrom() != null ? Timestamp.from(e.getDateFrom().toInstant()) : null);
                    ps.setObject(9, e.getDateTo() != null ? Timestamp.from(e.getDateTo().toInstant()) : null);
                    ps.setObject(10, e.getFreezeAt() != null ? Timestamp.from(e.getFreezeAt().toInstant()) : null);
                    ps.setObject(11, e.getParticipationDeadline() != null ? Timestamp.from(e.getParticipationDeadline().toInstant()) : null);
                    ps.setString(12, e.getDescription());
                    ps.setString(13, e.getMechanic());
                    ps.setObject(14, e.getIsParticipating());
                    ps.setString(15, e.getRawPayload());
                    ps.setLong(16, e.getJobExecutionId());
                    ps.setObject(17, e.getSyncedAt() != null ? Timestamp.from(e.getSyncedAt().toInstant()) : null);
                });
    }
}
