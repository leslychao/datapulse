package io.datapulse.analytics.domain.materializer.fact;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
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
public class FactProductCostMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "fact_product_cost";

    private static final String PG_QUERY = """
            SELECT cp.id            AS cost_id,
                   cp.seller_sku_id,
                   cp.cost_price,
                   cp.currency,
                   cp.valid_from,
                   cp.valid_to
            FROM cost_profile cp
            ORDER BY cp.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO fact_product_cost
            (cost_id, seller_sku_id, cost_price, currency, valid_from, valid_to, ver)
            VALUES (?, ?, ?, ?, ?, ?, ?)
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

        log.info("Materialized fact_product_cost: rows={}", total);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();

        List<Map<String, Object>> rows = jdbc.pg().queryForList("""
                SELECT cp.id            AS cost_id,
                       cp.seller_sku_id,
                       cp.cost_price,
                       cp.currency,
                       cp.valid_from,
                       cp.valid_to
                FROM cost_profile cp
                WHERE cp.updated_at > NOW() - INTERVAL '1 hour'
                """, Map.of());

        if (rows.isEmpty()) {
            return;
        }

        insertBatch(rows, ver);
        log.info("Incremental fact_product_cost: rows={}", rows.size());
    }

    private void insertBatch(List<Map<String, Object>> rows, long ver) {
        jdbc.ch().batchUpdate(CH_INSERT, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, ((Number) row.get("cost_id")).longValue());
            ps.setLong(2, ((Number) row.get("seller_sku_id")).longValue());
            ps.setBigDecimal(3, (BigDecimal) row.get("cost_price"));
            ps.setString(4, (String) row.get("currency"));

            LocalDate validFrom = (LocalDate) row.get("valid_from");
            ps.setDate(5, Date.valueOf(validFrom));

            LocalDate validTo = (LocalDate) row.get("valid_to");
            if (validTo != null) {
                ps.setDate(6, Date.valueOf(validTo));
            } else {
                ps.setNull(6, java.sql.Types.DATE);
            }

            ps.setLong(7, ver);
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
