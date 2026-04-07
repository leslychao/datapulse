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
public class FactFinanceMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "fact_finance";

    private static final String PG_QUERY = """
            SELECT workspace_id, id, connection_id, source_platform, entry_type,
                   posting_id, order_id, seller_sku_id, warehouse_id,
                   revenue_amount, marketplace_commission_amount, acquiring_commission_amount,
                   logistics_cost_amount, storage_cost_amount, penalties_amount,
                   acceptance_cost_amount, marketing_cost_amount, other_marketplace_charges_amount,
                   compensation_amount, refund_amount, net_payout,
                   entry_date, attribution_level, fulfillment_type, job_execution_id
            FROM canonical_finance_entry
            ORDER BY id
            LIMIT :limit OFFSET :offset
            """;

    private static final String PG_INCREMENTAL_QUERY = """
            SELECT workspace_id, id, connection_id, source_platform, entry_type,
                   posting_id, order_id, seller_sku_id, warehouse_id,
                   revenue_amount, marketplace_commission_amount, acquiring_commission_amount,
                   logistics_cost_amount, storage_cost_amount, penalties_amount,
                   acceptance_cost_amount, marketing_cost_amount, other_marketplace_charges_amount,
                   compensation_amount, refund_amount, net_payout,
                   entry_date, attribution_level, fulfillment_type, job_execution_id
            FROM canonical_finance_entry
            WHERE job_execution_id = :jobExecutionId
            """;

    private static final String CH_INSERT = """
            INSERT INTO %s
            (workspace_id, connection_id, source_platform, entry_id, posting_id, order_id,
             seller_sku_id, warehouse_id, finance_date, entry_type, attribution_level,
             fulfillment_type,
             revenue_amount, marketplace_commission_amount, acquiring_commission_amount,
             logistics_cost_amount, storage_cost_amount, penalties_amount,
             marketing_cost_amount, acceptance_cost_amount, other_marketplace_charges_amount,
             compensation_amount, refund_amount, net_payout,
             job_execution_id, ver, materialized_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final MaterializationJdbc jdbc;
    private final AnalyticsProperties properties;

    @Override
    public void materializeFull() {
        long ver = Instant.now().toEpochMilli();
        Timestamp materializedAt = Timestamp.from(Instant.now());

        jdbc.fullMaterializeWithSwap(TABLE, staging -> {
            String chInsert = CH_INSERT.formatted(staging);
            int offset = 0;

            while (true) {
                List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_QUERY,
                        Map.of("limit", properties.batchSize(), "offset", offset));
                if (rows.isEmpty()) {
                    break;
                }

                insertBatch(rows, ver, materializedAt, chInsert);
                offset += properties.batchSize();
            }
        });

        log.info("Materialized {}", TABLE);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();
        Timestamp materializedAt = Timestamp.from(Instant.now());
        String chInsert = CH_INSERT.formatted(TABLE);

        List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_INCREMENTAL_QUERY,
                Map.of("jobExecutionId", jobExecutionId));
        if (rows.isEmpty()) {
            return;
        }

        insertBatch(rows, ver, materializedAt, chInsert);
        log.info("Incremental fact_finance: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    private void insertBatch(List<Map<String, Object>> rows, long ver,
            Timestamp materializedAt, String chInsert) {
        jdbc.ch().batchUpdate(chInsert, rows, rows.size(), (ps, row) -> {
            ps.setInt(1, ((Number) row.get("workspace_id")).intValue());
            ps.setInt(2, ((Number) row.get("connection_id")).intValue());
            ps.setString(3, (String) row.get("source_platform"));
            ps.setLong(4, ((Number) row.get("id")).longValue());
            ps.setString(5, (String) row.get("posting_id"));
            ps.setString(6, (String) row.get("order_id"));

            Number sellerSkuId = (Number) row.get("seller_sku_id");
            if (sellerSkuId != null) {
                ps.setLong(7, sellerSkuId.longValue());
            } else {
                ps.setNull(7, java.sql.Types.BIGINT);
            }

            Number warehouseId = (Number) row.get("warehouse_id");
            if (warehouseId != null) {
                ps.setInt(8, warehouseId.intValue());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }

            Timestamp entryDate = (Timestamp) row.get("entry_date");
            ps.setDate(9, Date.valueOf(entryDate.toLocalDateTime().toLocalDate()));
            ps.setString(10, (String) row.get("entry_type"));
            ps.setString(11, (String) row.get("attribution_level"));
            ps.setString(12, (String) row.get("fulfillment_type"));

            ps.setBigDecimal(13, (BigDecimal) row.get("revenue_amount"));
            ps.setBigDecimal(14, (BigDecimal) row.get("marketplace_commission_amount"));
            ps.setBigDecimal(15, (BigDecimal) row.get("acquiring_commission_amount"));
            ps.setBigDecimal(16, (BigDecimal) row.get("logistics_cost_amount"));
            ps.setBigDecimal(17, (BigDecimal) row.get("storage_cost_amount"));
            ps.setBigDecimal(18, (BigDecimal) row.get("penalties_amount"));
            ps.setBigDecimal(19, (BigDecimal) row.get("marketing_cost_amount"));
            ps.setBigDecimal(20, (BigDecimal) row.get("acceptance_cost_amount"));
            ps.setBigDecimal(21, (BigDecimal) row.get("other_marketplace_charges_amount"));
            ps.setBigDecimal(22, (BigDecimal) row.get("compensation_amount"));
            ps.setBigDecimal(23, (BigDecimal) row.get("refund_amount"));

            BigDecimal netPayout = (BigDecimal) row.get("net_payout");
            ps.setBigDecimal(24, netPayout != null ? netPayout : BigDecimal.ZERO);

            ps.setLong(25, ((Number) row.get("job_execution_id")).longValue());
            ps.setLong(26, ver);
            ps.setTimestamp(27, materializedAt);
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
