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
            INSERT INTO canonical_sale (connection_id, source_platform, external_sale_id,
                                        canonical_order_id, marketplace_offer_id, posting_id,
                                        seller_sku_id, sale_date, sale_amount, commission,
                                        quantity, currency,
                                        job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (connection_id, external_sale_id) DO UPDATE SET
                canonical_order_id = EXCLUDED.canonical_order_id,
                marketplace_offer_id = EXCLUDED.marketplace_offer_id,
                posting_id = EXCLUDED.posting_id,
                seller_sku_id = EXCLUDED.seller_sku_id,
                sale_date = EXCLUDED.sale_date,
                sale_amount = EXCLUDED.sale_amount,
                commission = EXCLUDED.commission,
                quantity = EXCLUDED.quantity,
                currency = EXCLUDED.currency,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (canonical_sale.canonical_order_id, canonical_sale.marketplace_offer_id,
                   canonical_sale.posting_id, canonical_sale.seller_sku_id,
                   canonical_sale.sale_date, canonical_sale.sale_amount,
                   canonical_sale.commission, canonical_sale.quantity,
                   canonical_sale.currency)
                IS DISTINCT FROM
                  (EXCLUDED.canonical_order_id, EXCLUDED.marketplace_offer_id,
                   EXCLUDED.posting_id, EXCLUDED.seller_sku_id,
                   EXCLUDED.sale_date, EXCLUDED.sale_amount,
                   EXCLUDED.commission, EXCLUDED.quantity,
                   EXCLUDED.currency)
            """;

    public void batchUpsert(List<CanonicalSaleEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getConnectionId());
                    ps.setString(2, e.getSourcePlatform());
                    ps.setString(3, e.getExternalSaleId());
                    ps.setObject(4, e.getCanonicalOrderId());
                    ps.setObject(5, e.getMarketplaceOfferId());
                    ps.setString(6, e.getPostingId());
                    ps.setObject(7, e.getSellerSkuId());
                    ps.setTimestamp(8, Timestamp.from(e.getSaleDate().toInstant()));
                    ps.setBigDecimal(9, e.getSaleAmount());
                    ps.setBigDecimal(10, e.getCommission());
                    ps.setInt(11, e.getQuantity());
                    ps.setString(12, e.getCurrency());
                    ps.setLong(13, e.getJobExecutionId());
                });
    }
}
