package io.datapulse.analytics.domain.materializer.fact;

import java.math.BigDecimal;
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
public class FactFinanceMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "fact_finance";

    private static final String PG_QUERY = """
            SELECT id, connection_id, source_platform, entry_type,
                   posting_id, order_id, seller_sku_id, warehouse_id,
                   revenue_amount, marketplace_commission_amount, acquiring_commission_amount,
                   logistics_cost_amount, storage_cost_amount, penalties_amount,
                   acceptance_cost_amount, marketing_cost_amount, other_marketplace_charges_amount,
                   compensation_amount, refund_amount, net_payout,
                   entry_date, attribution_level, job_execution_id
            FROM canonical_finance_entry
            ORDER BY id
            LIMIT :limit OFFSET :offset
            """;

    private static final String PG_INCREMENTAL_QUERY = """
            SELECT id, connection_id, source_platform, entry_type,
                   posting_id, order_id, seller_sku_id, warehouse_id,
                   revenue_amount, marketplace_commission_amount, acquiring_commission_amount,
                   logistics_cost_amount, storage_cost_amount, penalties_amount,
                   acceptance_cost_amount, marketing_cost_amount, other_marketplace_charges_amount,
                   compensation_amount, refund_amount, net_payout,
                   entry_date, attribution_level, job_execution_id
            FROM canonical_finance_entry
            WHERE job_execution_id = :jobExecutionId
            """;

    private static final String CH_INSERT = """
            INSERT INTO fact_finance
            (connection_id, source_platform, entry_id, posting_id, order_id,
             seller_sku_id, warehouse_id, finance_date, entry_type, attribution_level,
             revenue_amount, marketplace_commission_amount, acquiring_commission_amount,
             logistics_cost_amount, storage_cost_amount, penalties_amount,
             marketing_cost_amount, acceptance_cost_amount, other_marketplace_charges_amount,
             compensation_amount, refund_amount, net_payout,
             job_execution_id, ver, materialized_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final MaterializationJdbc jdbc;
    private final AnalyticsProperties properties;

    @Override
    public void materializeFull() {
        jdbc.ch().execute("TRUNCATE TABLE " + TABLE);

        long ver = Instant.now().toEpochMilli();
        Timestamp materializedAt = Timestamp.from(Instant.now());
        int offset = 0;
        int total = 0;

        while (true) {
            List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_QUERY,
                    Map.of("limit", properties.batchSize(), "offset", offset));
            if (rows.isEmpty()) {
                break;
            }

            insertBatch(rows, ver, materializedAt);
            total += rows.size();
            offset += properties.batchSize();
        }

        log.info("Materialized fact_finance: rows={}", total);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();
        Timestamp materializedAt = Timestamp.from(Instant.now());

        List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_INCREMENTAL_QUERY,
                Map.of("jobExecutionId", jobExecutionId));
        if (rows.isEmpty()) {
            return;
        }

        insertBatch(rows, ver, materializedAt);
        log.info("Incremental fact_finance: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    private void insertBatch(List<Map<String, Object>> rows, long ver, Timestamp materializedAt) {
        jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
            ps.setInt(1, ((Number) row.get("connection_id")).intValue());
            ps.setString(2, (String) row.get("source_platform"));
            ps.setLong(3, ((Number) row.get("id")).longValue());
            ps.setString(4, (String) row.get("posting_id"));
            ps.setString(5, (String) row.get("order_id"));

            Number sellerSkuId = (Number) row.get("seller_sku_id");
            if (sellerSkuId != null) {
                ps.setLong(6, sellerSkuId.longValue());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }

            Number warehouseId = (Number) row.get("warehouse_id");
            if (warehouseId != null) {
                ps.setInt(7, warehouseId.intValue());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }

            OffsetDateTime entryDate = (OffsetDateTime) row.get("entry_date");
            ps.setDate(8, Date.valueOf(entryDate.toLocalDate()));
            ps.setString(9, (String) row.get("entry_type"));
            ps.setString(10, (String) row.get("attribution_level"));

            ps.setBigDecimal(11, (BigDecimal) row.get("revenue_amount"));
            ps.setBigDecimal(12, (BigDecimal) row.get("marketplace_commission_amount"));
            ps.setBigDecimal(13, (BigDecimal) row.get("acquiring_commission_amount"));
            ps.setBigDecimal(14, (BigDecimal) row.get("logistics_cost_amount"));
            ps.setBigDecimal(15, (BigDecimal) row.get("storage_cost_amount"));
            ps.setBigDecimal(16, (BigDecimal) row.get("penalties_amount"));
            ps.setBigDecimal(17, (BigDecimal) row.get("marketing_cost_amount"));
            ps.setBigDecimal(18, (BigDecimal) row.get("acceptance_cost_amount"));
            ps.setBigDecimal(19, (BigDecimal) row.get("other_marketplace_charges_amount"));
            ps.setBigDecimal(20, (BigDecimal) row.get("compensation_amount"));
            ps.setBigDecimal(21, (BigDecimal) row.get("refund_amount"));

            BigDecimal netPayout = (BigDecimal) row.get("net_payout");
            ps.setBigDecimal(22, netPayout != null ? netPayout : BigDecimal.ZERO);

            ps.setLong(23, ((Number) row.get("job_execution_id")).longValue());
            ps.setLong(24, ver);
            ps.setTimestamp(25, materializedAt);
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
