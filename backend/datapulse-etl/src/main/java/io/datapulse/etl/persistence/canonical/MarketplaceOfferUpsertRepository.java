package io.datapulse.etl.persistence.canonical;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MarketplaceOfferUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO marketplace_offer (seller_sku_id, marketplace_connection_id, marketplace_sku,
                                           marketplace_sku_alt, name, category_id, status, url, image_url,
                                           job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (seller_sku_id, marketplace_connection_id, marketplace_sku) DO UPDATE SET
                marketplace_sku_alt = EXCLUDED.marketplace_sku_alt,
                name = EXCLUDED.name,
                category_id = EXCLUDED.category_id,
                status = EXCLUDED.status,
                url = EXCLUDED.url,
                image_url = EXCLUDED.image_url,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (marketplace_offer.marketplace_sku_alt, marketplace_offer.name,
                   marketplace_offer.category_id, marketplace_offer.status,
                   marketplace_offer.url, marketplace_offer.image_url)
                IS DISTINCT FROM
                  (EXCLUDED.marketplace_sku_alt, EXCLUDED.name,
                   EXCLUDED.category_id, EXCLUDED.status,
                   EXCLUDED.url, EXCLUDED.image_url)
            """;

    public void batchUpsert(List<MarketplaceOfferEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getSellerSkuId());
                    ps.setLong(2, e.getMarketplaceConnectionId());
                    ps.setString(3, e.getMarketplaceSku());
                    ps.setString(4, e.getMarketplaceSkuAlt());
                    ps.setString(5, e.getName());
                    ps.setObject(6, e.getCategoryId());
                    ps.setString(7, e.getStatus());
                    ps.setString(8, e.getUrl());
                    ps.setString(9, e.getImageUrl());
                    ps.setLong(10, e.getJobExecutionId());
                });
    }
}
