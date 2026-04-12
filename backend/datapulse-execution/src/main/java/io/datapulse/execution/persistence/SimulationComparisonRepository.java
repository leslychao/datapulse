package io.datapulse.execution.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SimulationComparisonRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COMPARISON_ITEMS_SQL = """
            SELECT sos.marketplace_offer_id,
                   mo.marketplace_sku,
                   sos.simulated_price,
                   sos.canonical_price_at_simulation,
                   cpc.price AS current_real_price,
                   sos.price_delta,
                   sos.price_delta_pct,
                   sos.previous_simulated_price,
                   sos.simulated_at,
                   sos.price_action_id
            FROM simulated_offer_state sos
            JOIN marketplace_offer mo ON sos.marketplace_offer_id = mo.id
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            LEFT JOIN canonical_price_current cpc ON sos.marketplace_offer_id = cpc.marketplace_offer_id
            WHERE sos.workspace_id = :workspaceId
              AND mc.workspace_id = :workspaceId
              AND mc.marketplace_type = :sourcePlatform
            ORDER BY ABS(sos.price_delta_pct) DESC NULLS LAST
            """;

    private static final String SUMMARY_SQL = """
            SELECT COUNT(*)                                         AS total_simulated,
                   COALESCE(AVG(sos.price_delta_pct), 0)            AS avg_delta_pct,
                   COUNT(*) FILTER (WHERE sos.price_delta > 0)      AS count_increase,
                   COUNT(*) FILTER (WHERE sos.price_delta < 0)      AS count_decrease,
                   COUNT(*) FILTER (WHERE sos.price_delta = 0)      AS count_unchanged,
                   COALESCE(SUM(sos.price_delta), 0)                AS total_delta_sum
            FROM simulated_offer_state sos
            JOIN marketplace_offer mo ON sos.marketplace_offer_id = mo.id
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            WHERE sos.workspace_id = :workspaceId
              AND mc.workspace_id = :workspaceId
              AND mc.marketplace_type = :sourcePlatform
            """;

    private static final String COVERAGE_SQL = """
            SELECT COUNT(DISTINCT sos.marketplace_offer_id) AS simulated_count,
                   COUNT(DISTINCT mo.id)                    AS total_offers
            FROM marketplace_offer mo
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            LEFT JOIN simulated_offer_state sos
                   ON mo.id = sos.marketplace_offer_id AND sos.workspace_id = :workspaceId
            WHERE mc.workspace_id = :workspaceId
              AND mc.marketplace_type = :sourcePlatform
            """;

    public List<SimulationComparisonRow> findComparisonItems(long workspaceId,
                                                               String sourcePlatform) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("sourcePlatform", sourcePlatform);

        return jdbc.query(COMPARISON_ITEMS_SQL, params, (rs, rowNum) -> new SimulationComparisonRow(
                rs.getLong("marketplace_offer_id"),
                rs.getString("marketplace_sku"),
                rs.getBigDecimal("simulated_price"),
                rs.getBigDecimal("canonical_price_at_simulation"),
                rs.getBigDecimal("current_real_price"),
                rs.getBigDecimal("price_delta"),
                rs.getBigDecimal("price_delta_pct"),
                rs.getBigDecimal("previous_simulated_price"),
                rs.getObject("simulated_at", OffsetDateTime.class),
                rs.getLong("price_action_id")
        ));
    }

    public SimulationSummaryRow findSummary(long workspaceId, String sourcePlatform) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("sourcePlatform", sourcePlatform);

        return jdbc.queryForObject(SUMMARY_SQL, params, (rs, rowNum) -> new SimulationSummaryRow(
                rs.getLong("total_simulated"),
                rs.getBigDecimal("avg_delta_pct"),
                rs.getLong("count_increase"),
                rs.getLong("count_decrease"),
                rs.getLong("count_unchanged"),
                rs.getBigDecimal("total_delta_sum")
        ));
    }

    public CoverageRow findCoverage(long workspaceId, String sourcePlatform) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("sourcePlatform", sourcePlatform);

        return jdbc.queryForObject(COVERAGE_SQL, params, (rs, rowNum) -> new CoverageRow(
                rs.getLong("simulated_count"),
                rs.getLong("total_offers")
        ));
    }

    private static final String PREVIEW_BY_DECISION_SQL = """
            SELECT sos.marketplace_offer_id,
                   mo.marketplace_sku,
                   sos.simulated_price,
                   sos.canonical_price_at_simulation,
                   cpc.price AS current_real_price,
                   sos.price_delta,
                   sos.price_delta_pct,
                   sos.previous_simulated_price,
                   sos.simulated_at,
                   sos.price_action_id
            FROM simulated_offer_state sos
            JOIN marketplace_offer mo ON sos.marketplace_offer_id = mo.id
            JOIN price_action pa ON sos.price_action_id = pa.id
            LEFT JOIN canonical_price_current cpc ON sos.marketplace_offer_id = cpc.marketplace_offer_id
            WHERE pa.price_decision_id = :decisionId
              AND sos.workspace_id = :workspaceId
            ORDER BY ABS(sos.price_delta_pct) DESC NULLS LAST
            """;

    public List<SimulationComparisonRow> findByDecision(long workspaceId, long decisionId) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("decisionId", decisionId);

        return jdbc.query(PREVIEW_BY_DECISION_SQL, params, (rs, rowNum) -> new SimulationComparisonRow(
                rs.getLong("marketplace_offer_id"),
                rs.getString("marketplace_sku"),
                rs.getBigDecimal("simulated_price"),
                rs.getBigDecimal("canonical_price_at_simulation"),
                rs.getBigDecimal("current_real_price"),
                rs.getBigDecimal("price_delta"),
                rs.getBigDecimal("price_delta_pct"),
                rs.getBigDecimal("previous_simulated_price"),
                rs.getObject("simulated_at", OffsetDateTime.class),
                rs.getLong("price_action_id")
        ));
    }

    public record SimulationComparisonRow(
            long marketplaceOfferId,
            String marketplaceSku,
            BigDecimal simulatedPrice,
            BigDecimal canonicalPriceAtSimulation,
            BigDecimal currentRealPrice,
            BigDecimal priceDelta,
            BigDecimal priceDeltaPct,
            BigDecimal previousSimulatedPrice,
            OffsetDateTime simulatedAt,
            long priceActionId
    ) {
    }

    public record SimulationSummaryRow(
            long totalSimulated,
            BigDecimal avgDeltaPct,
            long countIncrease,
            long countDecrease,
            long countUnchanged,
            BigDecimal totalDeltaSum
    ) {
    }

    public record CoverageRow(
            long simulatedCount,
            long totalOffers
    ) {
    }
}
