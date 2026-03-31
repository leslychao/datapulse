package io.datapulse.etl.persistence.canonical;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Batch upsert for seller_sku table.
 *
 * <p><b>Not yet wired</b> into any EventSource. Will be used when
 * product hierarchy resolution (product_master → seller_sku → marketplace_offer)
 * is connected to the PRODUCT_DICT pipeline.</p>
 */
@Repository
@RequiredArgsConstructor
public class SellerSkuUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO seller_sku (product_master_id, sku_code, barcode,
                                    job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, now(), now())
            ON CONFLICT (product_master_id, sku_code) DO UPDATE SET
                barcode = EXCLUDED.barcode,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (seller_sku.barcode)
                IS DISTINCT FROM (EXCLUDED.barcode)
            """;

    public void batchUpsert(List<SellerSkuEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getProductMasterId());
                    ps.setString(2, e.getSkuCode());
                    ps.setString(3, e.getBarcode());
                    ps.setLong(4, e.getJobExecutionId());
                });
    }
}
