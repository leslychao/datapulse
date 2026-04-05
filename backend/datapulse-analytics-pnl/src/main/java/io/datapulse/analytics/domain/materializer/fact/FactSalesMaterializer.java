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
public class FactSalesMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "fact_sales";

    private static final String PG_QUERY = """
            SELECT cs.id              AS sale_id,
                   cs.connection_id,
                   cs.source_platform,
                   cs.fulfillment_type,
                   cs.posting_id,
                   co.external_order_id AS order_id,
                   cs.seller_sku_id,
                   cs.marketplace_offer_id AS product_id,
                   cs.quantity,
                   cs.sale_amount,
                   cs.sale_date,
                   cs.job_execution_id
            FROM canonical_sale cs
            LEFT JOIN canonical_order co ON cs.canonical_order_id = co.id
            ORDER BY cs.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO %s
            (sale_id, connection_id, source_platform, fulfillment_type,
             posting_id, order_id,
             seller_sku_id, product_id, quantity, sale_amount, sale_date,
             job_execution_id, ver)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final MaterializationJdbc jdbc;
    private final AnalyticsProperties properties;

    @Override
    public void materializeFull() {
        long ver = Instant.now().toEpochMilli();

        jdbc.fullMaterializeWithSwap(TABLE, staging -> {
            String chInsert = CH_INSERT.formatted(staging);
            int offset = 0;

            while (true) {
                List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_QUERY,
                        Map.of("limit", properties.batchSize(), "offset", offset));
                if (rows.isEmpty()) {
                    break;
                }

                insertBatch(rows, ver, chInsert);
                offset += properties.batchSize();
            }
        });

        log.info("Materialized {}", TABLE);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();
        String chInsert = CH_INSERT.formatted(TABLE);

        List<Map<String, Object>> rows = jdbc.pg().queryForList("""
                SELECT cs.id              AS sale_id,
                       cs.connection_id,
                       cs.source_platform,
                       cs.fulfillment_type,
                       cs.posting_id,
                       co.external_order_id AS order_id,
                       cs.seller_sku_id,
                       cs.marketplace_offer_id AS product_id,
                       cs.quantity,
                       cs.sale_amount,
                       cs.sale_date,
                       cs.job_execution_id
                FROM canonical_sale cs
                LEFT JOIN canonical_order co ON cs.canonical_order_id = co.id
                WHERE cs.job_execution_id = :jobExecutionId
                """, Map.of("jobExecutionId", jobExecutionId));

        if (rows.isEmpty()) {
            return;
        }

        insertBatch(rows, ver, chInsert);
        log.info("Incremental fact_sales: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    private void insertBatch(List<Map<String, Object>> rows, long ver, String chInsert) {
        jdbc.ch().batchUpdate(chInsert, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, ((Number) row.get("sale_id")).longValue());
            ps.setInt(2, ((Number) row.get("connection_id")).intValue());
            ps.setString(3, (String) row.get("source_platform"));
            ps.setString(4, (String) row.get("fulfillment_type"));
            ps.setString(5, (String) row.get("posting_id"));
            ps.setString(6, (String) row.get("order_id"));

            Number sellerSkuId = (Number) row.get("seller_sku_id");
            if (sellerSkuId != null) {
                ps.setLong(7, sellerSkuId.longValue());
            } else {
                ps.setNull(7, java.sql.Types.BIGINT);
            }

            Number productId = (Number) row.get("product_id");
            if (productId != null) {
                ps.setLong(8, productId.longValue());
            } else {
                ps.setNull(8, java.sql.Types.BIGINT);
            }

            ps.setInt(9, ((Number) row.get("quantity")).intValue());
            ps.setBigDecimal(10, (BigDecimal) row.get("sale_amount"));

            Timestamp saleDate = (Timestamp) row.get("sale_date");
            ps.setDate(11, Date.valueOf(saleDate.toLocalDateTime().toLocalDate()));

            ps.setLong(12, ((Number) row.get("job_execution_id")).longValue());
            ps.setLong(13, ver);
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
