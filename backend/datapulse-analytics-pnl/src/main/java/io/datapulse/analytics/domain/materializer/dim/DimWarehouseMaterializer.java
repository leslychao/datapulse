package io.datapulse.analytics.domain.materializer.dim;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.config.AnalyticsProperties;
import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import io.datapulse.analytics.domain.MaterializationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DimWarehouseMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "dim_warehouse";

    private static final String PG_QUERY = """
            SELECT w.id                    AS warehouse_id,
                   w.external_warehouse_id,
                   w.name,
                   w.warehouse_type,
                   w.marketplace_type
            FROM warehouse w
            ORDER BY w.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO dim_warehouse
            (warehouse_id, external_warehouse_id, name, warehouse_type, marketplace_type, ver)
            VALUES (?, ?, ?, ?, ?, ?)
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

            jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
                ps.setInt(1, ((Number) row.get("warehouse_id")).intValue());
                ps.setString(2, (String) row.get("external_warehouse_id"));
                ps.setString(3, (String) row.get("name"));
                ps.setString(4, (String) row.get("warehouse_type"));
                ps.setString(5, (String) row.get("marketplace_type"));
                ps.setLong(6, ver);
            });

            total += rows.size();
            offset += properties.batchSize();
        }

        log.info("Materialized dim_warehouse: rows={}", total);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();

        List<Map<String, Object>> rows = jdbc.pg().queryForList("""
                SELECT w.id                    AS warehouse_id,
                       w.external_warehouse_id,
                       w.name,
                       w.warehouse_type,
                       w.marketplace_type
                FROM warehouse w
                WHERE w.job_execution_id = :jobExecutionId
                """, Map.of("jobExecutionId", jobExecutionId));

        if (rows.isEmpty()) {
            return;
        }

        jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
            ps.setInt(1, ((Number) row.get("warehouse_id")).intValue());
            ps.setString(2, (String) row.get("external_warehouse_id"));
            ps.setString(3, (String) row.get("name"));
            ps.setString(4, (String) row.get("warehouse_type"));
            ps.setString(5, (String) row.get("marketplace_type"));
            ps.setLong(6, ver);
        });

        log.info("Incremental dim_warehouse: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    @Override
    public String tableName() {
        return TABLE;
    }

    @Override
    public MaterializationPhase phase() {
        return MaterializationPhase.DIMENSION;
    }
}
