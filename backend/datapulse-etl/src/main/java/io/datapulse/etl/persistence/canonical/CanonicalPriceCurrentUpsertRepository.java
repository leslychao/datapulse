package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CanonicalPriceCurrentUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_price_current (marketplace_offer_id, price, discount_price, discount_pct,
                                                 currency, min_price, max_price,
                                                 job_execution_id, captured_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (marketplace_offer_id) DO UPDATE SET
                price = EXCLUDED.price,
                discount_price = EXCLUDED.discount_price,
                discount_pct = EXCLUDED.discount_pct,
                currency = EXCLUDED.currency,
                min_price = EXCLUDED.min_price,
                max_price = EXCLUDED.max_price,
                job_execution_id = EXCLUDED.job_execution_id,
                captured_at = EXCLUDED.captured_at,
                updated_at = now()
            WHERE (canonical_price_current.price, canonical_price_current.discount_price,
                   canonical_price_current.discount_pct, canonical_price_current.currency,
                   canonical_price_current.min_price, canonical_price_current.max_price)
                IS DISTINCT FROM
                  (EXCLUDED.price, EXCLUDED.discount_price,
                   EXCLUDED.discount_pct, EXCLUDED.currency,
                   EXCLUDED.min_price, EXCLUDED.max_price)
            """;

    public void batchUpsert(List<CanonicalPriceCurrentEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getMarketplaceOfferId());
                    ps.setBigDecimal(2, e.getPrice());
                    ps.setBigDecimal(3, e.getDiscountPrice());
                    ps.setBigDecimal(4, e.getDiscountPct());
                    ps.setString(5, e.getCurrency());
                    ps.setBigDecimal(6, e.getMinPrice());
                    ps.setBigDecimal(7, e.getMaxPrice());
                    ps.setLong(8, e.getJobExecutionId());
                    ps.setTimestamp(9, Timestamp.from(e.getCapturedAt().toInstant()));
                });
    }
}
