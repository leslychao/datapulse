package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CanonicalPromoCampaignUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_promo_campaign (connection_id, external_promo_id, source_platform,
                                                  promo_name, promo_type, status,
                                                  date_from, date_to, freeze_at, participation_deadline,
                                                  description, mechanic, is_participating,
                                                  raw_payload, job_execution_id, synced_at,
                                                  created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, now(), now())
            ON CONFLICT (connection_id, external_promo_id) DO UPDATE SET
                source_platform = EXCLUDED.source_platform,
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

    public void batchUpsert(List<CanonicalPromoCampaignEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getConnectionId());
                    ps.setString(2, e.getExternalPromoId());
                    ps.setString(3, e.getSourcePlatform());
                    ps.setString(4, e.getPromoName());
                    ps.setString(5, e.getPromoType());
                    ps.setString(6, e.getStatus());
                    ps.setObject(7, e.getDateFrom() != null ? Timestamp.from(e.getDateFrom().toInstant()) : null);
                    ps.setObject(8, e.getDateTo() != null ? Timestamp.from(e.getDateTo().toInstant()) : null);
                    ps.setObject(9, e.getFreezeAt() != null ? Timestamp.from(e.getFreezeAt().toInstant()) : null);
                    ps.setObject(10, e.getParticipationDeadline() != null ? Timestamp.from(e.getParticipationDeadline().toInstant()) : null);
                    ps.setString(11, e.getDescription());
                    ps.setString(12, e.getMechanic());
                    ps.setObject(13, e.getIsParticipating());
                    ps.setString(14, e.getRawPayload());
                    ps.setLong(15, e.getJobExecutionId());
                    ps.setObject(16, e.getSyncedAt() != null ? Timestamp.from(e.getSyncedAt().toInstant()) : null);
                });
    }
}
