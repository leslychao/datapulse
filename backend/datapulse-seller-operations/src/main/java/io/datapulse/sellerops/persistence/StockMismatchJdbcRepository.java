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
public class StockMismatchJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String PG_STOCK_QUERY = """
            SELECT
                mo.id                        AS offer_id,
                mo.name                      AS offer_name,
                ss.sku_code,
                mc.name                      AS connection_name,
                mc.id                        AS connection_id,
                mc.workspace_id,
                COALESCE(SUM(csc.available), 0) AS canonical_stock
            FROM marketplace_offer mo
            JOIN seller_sku ss ON ss.id = mo.seller_sku_id
            JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
            LEFT JOIN canonical_stock_current csc ON csc.marketplace_offer_id = mo.id
            WHERE mc.workspace_id = :workspaceId
              AND mo.status = 'ACTIVE'
            GROUP BY mo.id, mo.name, ss.sku_code, mc.name, mc.id, mc.workspace_id
            """;

    public List<StockCandidate> findCanonicalStocks(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return jdbc.query(PG_STOCK_QUERY, params, this::mapCandidate);
    }

    private StockCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new StockCandidate(
                rs.getLong("offer_id"),
                rs.getString("offer_name"),
                rs.getString("sku_code"),
                rs.getString("connection_name"),
                rs.getLong("connection_id"),
                rs.getLong("workspace_id"),
                rs.getInt("canonical_stock")
        );
    }

    public record StockCandidate(
            long offerId,
            String offerName,
            String skuCode,
            String connectionName,
            long connectionId,
            long workspaceId,
            int canonicalStock
    ) {
    }
}
