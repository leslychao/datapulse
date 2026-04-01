package io.datapulse.sellerops.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PriceMismatchJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String PRICE_MISMATCH_QUERY = """
            SELECT
                mo.id                       AS offer_id,
                mo.name                     AS offer_name,
                ss.sku_code,
                mc.name                     AS connection_name,
                mc.id                       AS connection_id,
                mc.workspace_id,
                cpc.price                   AS current_price,
                latest_pa.target_price      AS expected_price
            FROM marketplace_offer mo
            JOIN seller_sku ss ON ss.id = mo.seller_sku_id
            JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
            JOIN canonical_price_current cpc ON cpc.marketplace_offer_id = mo.id
            JOIN LATERAL (
                SELECT pa.target_price
                FROM price_action pa
                WHERE pa.marketplace_offer_id = mo.id
                  AND pa.status = 'SUCCEEDED'
                  AND pa.execution_mode = 'LIVE'
                ORDER BY pa.created_at DESC
                LIMIT 1
            ) latest_pa ON true
            WHERE mc.workspace_id = :workspaceId
              AND cpc.price IS NOT NULL
              AND ABS(cpc.price - latest_pa.target_price) / NULLIF(latest_pa.target_price, 0) * 100 > :thresholdPct
            """;

    public List<PriceMismatchCandidate> findPriceMismatches(long workspaceId,
                                                             BigDecimal thresholdPct) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("thresholdPct", thresholdPct);

        return jdbc.query(PRICE_MISMATCH_QUERY, params, this::mapCandidate);
    }

    private PriceMismatchCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new PriceMismatchCandidate(
                rs.getLong("offer_id"),
                rs.getString("offer_name"),
                rs.getString("sku_code"),
                rs.getString("connection_name"),
                rs.getLong("connection_id"),
                rs.getLong("workspace_id"),
                rs.getBigDecimal("current_price"),
                rs.getBigDecimal("expected_price")
        );
    }

    public record PriceMismatchCandidate(
            long offerId,
            String offerName,
            String skuCode,
            String connectionName,
            long connectionId,
            long workspaceId,
            BigDecimal currentPrice,
            BigDecimal expectedPrice
    ) {
    }
}
