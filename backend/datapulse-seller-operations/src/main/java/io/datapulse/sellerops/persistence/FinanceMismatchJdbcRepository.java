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
public class FinanceMismatchJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FINANCE_GAP_QUERY = """
            SELECT
                mc.id                       AS connection_id,
                mc.workspace_id,
                mc.name                     AS connection_name,
                mc.marketplace_type,
                MAX(cfe.entry_date)         AS last_finance_date
            FROM marketplace_connection mc
            LEFT JOIN canonical_finance_entry cfe
                ON cfe.connection_id = mc.id
            WHERE mc.workspace_id = :workspaceId
              AND mc.status = 'ACTIVE'
            GROUP BY mc.id, mc.workspace_id, mc.name, mc.marketplace_type
            HAVING MAX(cfe.entry_date) IS NULL
                OR MAX(cfe.entry_date) < CURRENT_DATE - make_interval(hours => :gapHours)
            """;

    public List<FinanceGapCandidate> findFinanceGaps(long workspaceId, int gapHoursThreshold) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("gapHours", gapHoursThreshold);
        return jdbc.query(FINANCE_GAP_QUERY, params, this::mapCandidate);
    }

    private FinanceGapCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new FinanceGapCandidate(
                rs.getLong("connection_id"),
                rs.getLong("workspace_id"),
                rs.getString("connection_name"),
                rs.getString("marketplace_type"),
                rs.getDate("last_finance_date") != null
                        ? rs.getDate("last_finance_date").toLocalDate() : null
        );
    }

    public record FinanceGapCandidate(
            long connectionId,
            long workspaceId,
            String connectionName,
            String marketplaceType,
            java.time.LocalDate lastFinanceDate
    ) {
    }
}
