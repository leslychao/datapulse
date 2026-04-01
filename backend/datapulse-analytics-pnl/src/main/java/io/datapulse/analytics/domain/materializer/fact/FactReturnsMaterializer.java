package io.datapulse.analytics.domain.materializer.fact;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.OffsetDateTime;
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
public class FactReturnsMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "fact_returns";

    private static final String PG_QUERY = """
            SELECT cr.id                AS return_id,
                   cr.connection_id,
                   cr.source_platform,
                   cr.external_return_id,
                   cr.seller_sku_id,
                   cr.marketplace_offer_id AS product_id,
                   cr.quantity,
                   cr.return_amount,
                   cr.return_reason,
                   cr.return_date,
                   cr.job_execution_id
            FROM canonical_return cr
            ORDER BY cr.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO fact_returns
            (return_id, connection_id, source_platform, external_return_id,
             seller_sku_id, product_id, quantity, return_amount, return_reason,
             return_date, job_execution_id, ver)
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

            insertBatch(rows, ver);
            total += rows.size();
            offset += properties.batchSize();
        }

        log.info("Materialized fact_returns: rows={}", total);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();

        List<Map<String, Object>> rows = jdbc.pg().queryForList("""
                SELECT cr.id                AS return_id,
                       cr.connection_id,
                       cr.source_platform,
                       cr.external_return_id,
                       cr.seller_sku_id,
                       cr.marketplace_offer_id AS product_id,
                       cr.quantity,
                       cr.return_amount,
                       cr.return_reason,
                       cr.return_date,
                       cr.job_execution_id
                FROM canonical_return cr
                WHERE cr.job_execution_id = :jobExecutionId
                """, Map.of("jobExecutionId", jobExecutionId));

        if (rows.isEmpty()) {
            return;
        }

        insertBatch(rows, ver);
        log.info("Incremental fact_returns: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    private void insertBatch(List<Map<String, Object>> rows, long ver) {
        jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, ((Number) row.get("return_id")).longValue());
            ps.setInt(2, ((Number) row.get("connection_id")).intValue());
            ps.setString(3, (String) row.get("source_platform"));
            ps.setString(4, (String) row.get("external_return_id"));

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

            BigDecimal returnAmount = (BigDecimal) row.get("return_amount");
            if (returnAmount != null) {
                ps.setBigDecimal(8, returnAmount);
            } else {
                ps.setNull(8, java.sql.Types.DECIMAL);
            }

            ps.setString(9, (String) row.get("return_reason"));

            OffsetDateTime returnDate = (OffsetDateTime) row.get("return_date");
            ps.setDate(10, Date.valueOf(returnDate.toLocalDate()));

            ps.setLong(11, ((Number) row.get("job_execution_id")).longValue());
            ps.setLong(12, ver);
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
