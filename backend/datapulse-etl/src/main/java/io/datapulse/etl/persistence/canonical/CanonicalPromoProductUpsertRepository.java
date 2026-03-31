package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Batch upsert for canonical_promo_product table.
 *
 * <p><b>Not yet wired</b> into any EventSource. Will be used when
 * PROMO_SYNC EventSource implementations are connected.</p>
 */
@Repository
@RequiredArgsConstructor
public class CanonicalPromoProductUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_promo_product (canonical_promo_campaign_id, marketplace_offer_id,
                                                 participation_status, required_price, current_price,
                                                 max_promo_price, max_discount_pct,
                                                 min_stock_required, stock_available,
                                                 add_mode, participation_decision_source,
                                                 job_execution_id, synced_at,
                                                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (canonical_promo_campaign_id, marketplace_offer_id) DO UPDATE SET
                participation_status = EXCLUDED.participation_status,
                required_price = EXCLUDED.required_price,
                current_price = EXCLUDED.current_price,
                max_promo_price = EXCLUDED.max_promo_price,
                max_discount_pct = EXCLUDED.max_discount_pct,
                min_stock_required = EXCLUDED.min_stock_required,
                stock_available = EXCLUDED.stock_available,
                add_mode = EXCLUDED.add_mode,
                participation_decision_source = EXCLUDED.participation_decision_source,
                job_execution_id = EXCLUDED.job_execution_id,
                synced_at = EXCLUDED.synced_at,
                updated_at = now()
            WHERE (canonical_promo_product.participation_status, canonical_promo_product.required_price,
                   canonical_promo_product.current_price, canonical_promo_product.max_promo_price,
                   canonical_promo_product.max_discount_pct, canonical_promo_product.min_stock_required,
                   canonical_promo_product.stock_available, canonical_promo_product.add_mode,
                   canonical_promo_product.participation_decision_source)
                IS DISTINCT FROM
                  (EXCLUDED.participation_status, EXCLUDED.required_price,
                   EXCLUDED.current_price, EXCLUDED.max_promo_price,
                   EXCLUDED.max_discount_pct, EXCLUDED.min_stock_required,
                   EXCLUDED.stock_available, EXCLUDED.add_mode,
                   EXCLUDED.participation_decision_source)
            """;

    public void batchUpsert(List<CanonicalPromoProductEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getCanonicalPromoCampaignId());
                    ps.setLong(2, e.getMarketplaceOfferId());
                    ps.setString(3, e.getParticipationStatus());
                    ps.setBigDecimal(4, e.getRequiredPrice());
                    ps.setBigDecimal(5, e.getCurrentPrice());
                    ps.setBigDecimal(6, e.getMaxPromoPrice());
                    ps.setBigDecimal(7, e.getMaxDiscountPct());
                    ps.setObject(8, e.getMinStockRequired());
                    ps.setObject(9, e.getStockAvailable());
                    ps.setString(10, e.getAddMode());
                    ps.setString(11, e.getParticipationDecisionSource());
                    ps.setLong(12, e.getJobExecutionId());
                    ps.setObject(13, e.getSyncedAt() != null ? Timestamp.from(e.getSyncedAt().toInstant()) : null);
                });
    }
}
