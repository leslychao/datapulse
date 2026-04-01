package io.datapulse.analytics.domain.materializer.fact;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
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
public class FactOrdersMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "fact_orders";

    private static final String PG_QUERY = """
            SELECT co.id               AS order_id_pk,
                   co.connection_id,
                   co.source_platform,
                   co.external_order_id,
                   mo.seller_sku_id,
                   co.marketplace_offer_id AS product_id,
                   co.quantity,
                   co.price_per_unit,
                   COALESCE(co.total_amount, co.price_per_unit * co.quantity) AS total_amount,
                   co.order_date,
                   co.status,
                   co.fulfillment_type,
                   co.region,
                   co.job_execution_id
            FROM canonical_order co
            LEFT JOIN marketplace_offer mo ON co.marketplace_offer_id = mo.id
            ORDER BY co.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO fact_orders
            (order_id_pk, connection_id, source_platform, external_order_id,
             seller_sku_id, product_id, quantity, price_per_unit, total_amount,
             order_date, status, fulfillment_type, region, job_execution_id, ver)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

        log.info("Materialized fact_orders: rows={}", total);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();

        List<Map<String, Object>> rows = jdbc.pg().queryForList("""
                SELECT co.id               AS order_id_pk,
                       co.connection_id,
                       co.source_platform,
                       co.external_order_id,
                       mo.seller_sku_id,
                       co.marketplace_offer_id AS product_id,
                       co.quantity,
                       co.price_per_unit,
                       COALESCE(co.total_amount, co.price_per_unit * co.quantity) AS total_amount,
                       co.order_date,
                       co.status,
                       co.fulfillment_type,
                       co.region,
                       co.job_execution_id
                FROM canonical_order co
                LEFT JOIN marketplace_offer mo ON co.marketplace_offer_id = mo.id
                WHERE co.job_execution_id = :jobExecutionId
                """, Map.of("jobExecutionId", jobExecutionId));

        if (rows.isEmpty()) {
            return;
        }

        insertBatch(rows, ver);
        log.info("Incremental fact_orders: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    private void insertBatch(List<Map<String, Object>> rows, long ver) {
        jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, ((Number) row.get("order_id_pk")).longValue());
            ps.setInt(2, ((Number) row.get("connection_id")).intValue());
            ps.setString(3, (String) row.get("source_platform"));
            ps.setString(4, (String) row.get("external_order_id"));

            Number sellerSkuId = (Number) row.get("seller_sku_id");
            if (sellerSkuId != null) {
                ps.setLong(5, sellerSkuId.longValue());
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }

            Number productId = (Number) row.get("product_id");
            if (productId != null) {
                ps.setLong(6, productId.longValue());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }

            ps.setInt(7, ((Number) row.get("quantity")).intValue());
            ps.setBigDecimal(8, (BigDecimal) row.get("price_per_unit"));
            ps.setBigDecimal(9, (BigDecimal) row.get("total_amount"));

            Timestamp orderDate = (Timestamp) row.get("order_date");
            ps.setDate(10, Date.valueOf(orderDate.toLocalDateTime().toLocalDate()));

            ps.setString(11, (String) row.get("status"));
            ps.setString(12, (String) row.get("fulfillment_type"));
            ps.setString(13, (String) row.get("region"));
            ps.setLong(14, ((Number) row.get("job_execution_id")).longValue());
            ps.setLong(15, ver);
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
