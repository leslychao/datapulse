package io.datapulse.etl.persistence.canonical;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CanonicalStockCurrentUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO canonical_stock_current (marketplace_offer_id, warehouse_id,
                                                 available, reserved,
                                                 job_execution_id, captured_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (marketplace_offer_id, warehouse_id) DO UPDATE SET
                available = EXCLUDED.available,
                reserved = EXCLUDED.reserved,
                job_execution_id = EXCLUDED.job_execution_id,
                captured_at = EXCLUDED.captured_at,
                updated_at = now()
            WHERE (canonical_stock_current.available, canonical_stock_current.reserved)
                IS DISTINCT FROM (EXCLUDED.available, EXCLUDED.reserved)
            """;

    public void batchUpsert(List<CanonicalStockCurrentEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getMarketplaceOfferId());
                    ps.setLong(2, e.getWarehouseId());
                    ps.setInt(3, e.getAvailable());
                    ps.setInt(4, e.getReserved());
                    ps.setLong(5, e.getJobExecutionId());
                    ps.setTimestamp(6, Timestamp.from(e.getCapturedAt().toInstant()));
                });
    }
}
