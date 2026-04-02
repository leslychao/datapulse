package io.datapulse.sellerops.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PromoMismatchJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String PROMO_MISMATCH_QUERY = """
            SELECT
                cpp.marketplace_offer_id     AS offer_id,
                mo.name                      AS offer_name,
                ss.sku_code,
                mc.name                      AS connection_name,
                mc.id                        AS connection_id,
                mc.workspace_id,
                cpp.participation_status     AS canonical_status,
                latest_pa.outcome_status     AS action_outcome
            FROM canonical_promo_product cpp
            JOIN marketplace_offer mo ON mo.id = cpp.marketplace_offer_id
            JOIN seller_sku ss ON ss.id = mo.seller_sku_id
            JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
            JOIN LATERAL (
                SELECT
                    CASE pa.status
                        WHEN 'SUCCEEDED' THEN pd.decision_type
                        ELSE 'NONE'
                    END AS outcome_status
                FROM promo_action pa
                JOIN promo_decision pd ON pd.id = pa.promo_decision_id
                WHERE pd.canonical_promo_product_id = cpp.id
                ORDER BY pa.created_at DESC
                LIMIT 1
            ) latest_pa ON true
            WHERE mc.workspace_id = :workspaceId
              AND (
                  (cpp.participation_status = 'PARTICIPATING'
                      AND latest_pa.outcome_status IN ('DECLINE', 'DEACTIVATE'))
                  OR
                  (cpp.participation_status != 'PARTICIPATING'
                      AND latest_pa.outcome_status = 'PARTICIPATE')
              )
            """;

    public List<PromoMismatchCandidate> findPromoMismatches(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return jdbc.query(PROMO_MISMATCH_QUERY, params, this::mapCandidate);
    }

    private PromoMismatchCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new PromoMismatchCandidate(
                rs.getLong("offer_id"),
                rs.getString("offer_name"),
                rs.getString("sku_code"),
                rs.getString("connection_name"),
                rs.getLong("connection_id"),
                rs.getLong("workspace_id"),
                rs.getString("canonical_status"),
                rs.getString("action_outcome")
        );
    }

    public record PromoMismatchCandidate(
            long offerId,
            String offerName,
            String skuCode,
            String connectionName,
            long connectionId,
            long workspaceId,
            String canonicalStatus,
            String actionOutcome
    ) {
    }
}
