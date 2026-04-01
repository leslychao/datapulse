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
public class DimProductMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "dim_product";

    private static final String PG_QUERY = """
            SELECT mo.id              AS product_id,
                   mo.marketplace_connection_id AS connection_id,
                   mc.marketplace_type AS source_platform,
                   ss.id              AS seller_sku_id,
                   ss.product_master_id,
                   ss.sku_code,
                   mo.marketplace_sku,
                   COALESCE(mo.name, '') AS product_name,
                   pm.brand,
                   c.name             AS category,
                   mo.status
            FROM marketplace_offer mo
            JOIN seller_sku ss ON mo.seller_sku_id = ss.id
            JOIN product_master pm ON ss.product_master_id = pm.id
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            LEFT JOIN category c ON mo.category_id = c.id
            ORDER BY mo.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO dim_product
            (product_id, connection_id, source_platform, seller_sku_id, product_master_id,
             sku_code, marketplace_sku, product_name, brand, category, status, ver)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                ps.setLong(1, ((Number) row.get("product_id")).longValue());
                ps.setInt(2, ((Number) row.get("connection_id")).intValue());
                ps.setString(3, (String) row.get("source_platform"));
                ps.setLong(4, ((Number) row.get("seller_sku_id")).longValue());
                ps.setLong(5, ((Number) row.get("product_master_id")).longValue());
                ps.setString(6, (String) row.get("sku_code"));
                ps.setString(7, (String) row.get("marketplace_sku"));
                ps.setString(8, (String) row.get("product_name"));
                ps.setString(9, (String) row.get("brand"));
                ps.setString(10, (String) row.get("category"));
                ps.setString(11, (String) row.get("status"));
                ps.setLong(12, ver);
            });

            total += rows.size();
            offset += properties.batchSize();
        }

        log.info("Materialized dim_product: rows={}", total);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();

        String pgQuery = """
                SELECT mo.id              AS product_id,
                       mo.marketplace_connection_id AS connection_id,
                       mc.marketplace_type AS source_platform,
                       ss.id              AS seller_sku_id,
                       ss.product_master_id,
                       ss.sku_code,
                       mo.marketplace_sku,
                       COALESCE(mo.name, '') AS product_name,
                       pm.brand,
                       c.name             AS category,
                       mo.status
                FROM marketplace_offer mo
                JOIN seller_sku ss ON mo.seller_sku_id = ss.id
                JOIN product_master pm ON ss.product_master_id = pm.id
                JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
                LEFT JOIN category c ON mo.category_id = c.id
                WHERE mo.job_execution_id = :jobExecutionId
                   OR ss.job_execution_id = :jobExecutionId
                """;

        List<Map<String, Object>> rows = jdbc.pg().queryForList(pgQuery,
                Map.of("jobExecutionId", jobExecutionId));
        if (rows.isEmpty()) {
            return;
        }

        jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, ((Number) row.get("product_id")).longValue());
            ps.setInt(2, ((Number) row.get("connection_id")).intValue());
            ps.setString(3, (String) row.get("source_platform"));
            ps.setLong(4, ((Number) row.get("seller_sku_id")).longValue());
            ps.setLong(5, ((Number) row.get("product_master_id")).longValue());
            ps.setString(6, (String) row.get("sku_code"));
            ps.setString(7, (String) row.get("marketplace_sku"));
            ps.setString(8, (String) row.get("product_name"));
            ps.setString(9, (String) row.get("brand"));
            ps.setString(10, (String) row.get("category"));
            ps.setString(11, (String) row.get("status"));
            ps.setLong(12, ver);
        });

        log.info("Incremental dim_product: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
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
