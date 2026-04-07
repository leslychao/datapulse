package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CanonicalReturnUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_return (workspace_id, connection_id, source_platform,
                                          external_return_id, canonical_order_id,
                                          marketplace_offer_id, seller_sku_id,
                                          return_date, return_amount, return_reason,
                                          quantity, status, currency, fulfillment_type,
                                          job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (workspace_id, source_platform, external_return_id) DO UPDATE SET
                connection_id = EXCLUDED.connection_id,
                canonical_order_id = EXCLUDED.canonical_order_id,
                marketplace_offer_id = EXCLUDED.marketplace_offer_id,
                seller_sku_id = EXCLUDED.seller_sku_id,
                return_date = EXCLUDED.return_date,
                return_amount = EXCLUDED.return_amount,
                return_reason = EXCLUDED.return_reason,
                quantity = EXCLUDED.quantity,
                status = EXCLUDED.status,
                currency = EXCLUDED.currency,
                fulfillment_type = EXCLUDED.fulfillment_type,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (canonical_return.connection_id, canonical_return.canonical_order_id,
                   canonical_return.marketplace_offer_id, canonical_return.seller_sku_id,
                   canonical_return.return_date, canonical_return.return_amount,
                   canonical_return.return_reason, canonical_return.quantity,
                   canonical_return.status, canonical_return.currency,
                   canonical_return.fulfillment_type)
                IS DISTINCT FROM
                  (EXCLUDED.connection_id, EXCLUDED.canonical_order_id,
                   EXCLUDED.marketplace_offer_id, EXCLUDED.seller_sku_id,
                   EXCLUDED.return_date, EXCLUDED.return_amount,
                   EXCLUDED.return_reason, EXCLUDED.quantity,
                   EXCLUDED.status, EXCLUDED.currency,
                   EXCLUDED.fulfillment_type)
            """;

    public void batchUpsert(List<CanonicalReturnEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setLong(2, e.getConnectionId());
                    ps.setString(3, e.getSourcePlatform());
                    ps.setString(4, e.getExternalReturnId());
                    ps.setObject(5, e.getCanonicalOrderId());
                    ps.setObject(6, e.getMarketplaceOfferId());
                    ps.setObject(7, e.getSellerSkuId());
                    ps.setTimestamp(8, Timestamp.from(e.getReturnDate().toInstant()));
                    ps.setBigDecimal(9, e.getReturnAmount());
                    ps.setString(10, e.getReturnReason());
                    ps.setInt(11, e.getQuantity());
                    ps.setString(12, e.getStatus());
                    ps.setString(13, e.getCurrency());
                    ps.setString(14, e.getFulfillmentType());
                    ps.setLong(15, e.getJobExecutionId());
                });
    }
}
