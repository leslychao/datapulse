package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CanonicalSaleUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_sale (workspace_id, connection_id, source_platform,
                                        external_sale_id, canonical_order_id, marketplace_offer_id,
                                        posting_id, seller_sku_id, sale_date, sale_amount,
                                        commission, quantity, currency, fulfillment_type,
                                        job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (workspace_id, source_platform, external_sale_id) DO UPDATE SET
                connection_id = EXCLUDED.connection_id,
                canonical_order_id = EXCLUDED.canonical_order_id,
                marketplace_offer_id = EXCLUDED.marketplace_offer_id,
                posting_id = EXCLUDED.posting_id,
                seller_sku_id = EXCLUDED.seller_sku_id,
                sale_date = EXCLUDED.sale_date,
                sale_amount = EXCLUDED.sale_amount,
                commission = EXCLUDED.commission,
                quantity = EXCLUDED.quantity,
                currency = EXCLUDED.currency,
                fulfillment_type = EXCLUDED.fulfillment_type,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (canonical_sale.connection_id, canonical_sale.canonical_order_id,
                   canonical_sale.marketplace_offer_id, canonical_sale.posting_id,
                   canonical_sale.seller_sku_id, canonical_sale.sale_date,
                   canonical_sale.sale_amount, canonical_sale.commission,
                   canonical_sale.quantity, canonical_sale.currency,
                   canonical_sale.fulfillment_type)
                IS DISTINCT FROM
                  (EXCLUDED.connection_id, EXCLUDED.canonical_order_id,
                   EXCLUDED.marketplace_offer_id, EXCLUDED.posting_id,
                   EXCLUDED.seller_sku_id, EXCLUDED.sale_date,
                   EXCLUDED.sale_amount, EXCLUDED.commission,
                   EXCLUDED.quantity, EXCLUDED.currency,
                   EXCLUDED.fulfillment_type)
            """;

    public void batchUpsert(List<CanonicalSaleEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setLong(2, e.getConnectionId());
                    ps.setString(3, e.getSourcePlatform());
                    ps.setString(4, e.getExternalSaleId());
                    ps.setObject(5, e.getCanonicalOrderId());
                    ps.setObject(6, e.getMarketplaceOfferId());
                    ps.setString(7, e.getPostingId());
                    ps.setObject(8, e.getSellerSkuId());
                    ps.setTimestamp(9, Timestamp.from(e.getSaleDate().toInstant()));
                    ps.setBigDecimal(10, e.getSaleAmount());
                    ps.setBigDecimal(11, e.getCommission());
                    ps.setInt(12, e.getQuantity());
                    ps.setString(13, e.getCurrency());
                    ps.setString(14, e.getFulfillmentType());
                    ps.setLong(15, e.getJobExecutionId());
                });
    }
}
