package io.datapulse.analytics.domain.materializer.fact;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.config.AnalyticsProperties;
import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.domain.MaterializationJdbc;
import io.datapulse.analytics.domain.MaterializationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FactInventorySnapshotMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "fact_inventory_snapshot";

    private static final String PG_QUERY = """
            SELECT mo.marketplace_connection_id AS connection_id,
                   mc.marketplace_type          AS source_platform,
                   csc.marketplace_offer_id     AS product_id,
                   csc.warehouse_id,
                   csc.available,
                   csc.reserved,
                   csc.captured_at
            FROM canonical_stock_current csc
            JOIN marketplace_offer mo ON csc.marketplace_offer_id = mo.id
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            ORDER BY csc.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO fact_inventory_snapshot
            (connection_id, source_platform, product_id, warehouse_id,
             available, reserved, captured_at, captured_date, ver)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final MaterializationJdbc jdbc;
    private final AnalyticsProperties properties;

    @Override
    public void materializeFull() {
        jdbc.ch().execute("TRUNCATE TABLE " + TABLE);

        long ver = Instant.now().toEpochMilli();
        int offset = 0;
        int total = 0;

        while (true) {
            List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_QUERY,
                    Map.of("limit", properties.batchSize(), "offset", offset));
            if (rows.isEmpty()) {
                break;
            }

            insertBatch(rows, ver);
            total += rows.size();
            offset += properties.batchSize();
        }

        log.info("Materialized fact_inventory_snapshot: rows={}", total);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();

        List<Map<String, Object>> rows = jdbc.pg().queryForList("""
                SELECT mo.marketplace_connection_id AS connection_id,
                       mc.marketplace_type          AS source_platform,
                       csc.marketplace_offer_id     AS product_id,
                       csc.warehouse_id,
                       csc.available,
                       csc.reserved,
                       csc.captured_at
                FROM canonical_stock_current csc
                JOIN marketplace_offer mo ON csc.marketplace_offer_id = mo.id
                JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
                WHERE csc.job_execution_id = :jobExecutionId
                """, Map.of("jobExecutionId", jobExecutionId));

        if (rows.isEmpty()) {
            return;
        }

        insertBatch(rows, ver);
        log.info("Incremental fact_inventory_snapshot: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    private void insertBatch(List<Map<String, Object>> rows, long ver) {
        jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
            ps.setInt(1, ((Number) row.get("connection_id")).intValue());
            ps.setString(2, (String) row.get("source_platform"));
            ps.setLong(3, ((Number) row.get("product_id")).longValue());
            ps.setInt(4, ((Number) row.get("warehouse_id")).intValue());
            ps.setInt(5, ((Number) row.get("available")).intValue());

            Number reserved = (Number) row.get("reserved");
            if (reserved != null) {
                ps.setInt(6, reserved.intValue());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }

            OffsetDateTime capturedAt = (OffsetDateTime) row.get("captured_at");
            Timestamp ts = Timestamp.from(capturedAt.toInstant());
            ps.setTimestamp(7, ts);
            ps.setDate(8, Date.valueOf(capturedAt.toLocalDate()));
            ps.setLong(9, ver);
        });
    }

    @Override
    public String tableName() {
        return TABLE;
    }

    @Override
    public MaterializationPhase phase() {
        return MaterializationPhase.FACT;
    }
}
