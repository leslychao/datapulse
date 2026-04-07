package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CanonicalOrderUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_order (workspace_id, connection_id, source_platform,
                                         external_order_id, marketplace_offer_id, order_date,
                                         quantity, price_per_unit, total_amount, currency,
                                         status, fulfillment_type, region,
                                         job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (workspace_id, source_platform, external_order_id) DO UPDATE SET
                connection_id = EXCLUDED.connection_id,
                marketplace_offer_id = EXCLUDED.marketplace_offer_id,
                order_date = EXCLUDED.order_date,
                quantity = EXCLUDED.quantity,
                price_per_unit = EXCLUDED.price_per_unit,
                total_amount = EXCLUDED.total_amount,
                currency = EXCLUDED.currency,
                status = EXCLUDED.status,
                fulfillment_type = EXCLUDED.fulfillment_type,
                region = EXCLUDED.region,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (canonical_order.connection_id, canonical_order.marketplace_offer_id,
                   canonical_order.order_date, canonical_order.quantity,
                   canonical_order.price_per_unit, canonical_order.total_amount,
                   canonical_order.currency, canonical_order.status,
                   canonical_order.fulfillment_type, canonical_order.region)
                IS DISTINCT FROM
                  (EXCLUDED.connection_id, EXCLUDED.marketplace_offer_id,
                   EXCLUDED.order_date, EXCLUDED.quantity,
                   EXCLUDED.price_per_unit, EXCLUDED.total_amount,
                   EXCLUDED.currency, EXCLUDED.status,
                   EXCLUDED.fulfillment_type, EXCLUDED.region)
            """;

    public void batchUpsert(List<CanonicalOrderEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setLong(2, e.getConnectionId());
                    ps.setString(3, e.getSourcePlatform());
                    ps.setString(4, e.getExternalOrderId());
                    ps.setObject(5, e.getMarketplaceOfferId());
                    ps.setTimestamp(6, Timestamp.from(e.getOrderDate().toInstant()));
                    ps.setInt(7, e.getQuantity());
                    ps.setBigDecimal(8, e.getPricePerUnit());
                    ps.setBigDecimal(9, e.getTotalAmount());
                    ps.setString(10, e.getCurrency());
                    ps.setString(11, e.getStatus());
                    ps.setString(12, e.getFulfillmentType());
                    ps.setString(13, e.getRegion());
                    ps.setLong(14, e.getJobExecutionId());
                });
    }
}
