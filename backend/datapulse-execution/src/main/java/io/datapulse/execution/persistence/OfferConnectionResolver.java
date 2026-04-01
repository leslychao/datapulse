package io.datapulse.execution.persistence;

import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Resolves marketplace offer → connection mapping via a single JDBC query.
 * Avoids dependency on ETL module's JPA entity for MarketplaceOffer.
 */
@Repository
@RequiredArgsConstructor
public class OfferConnectionResolver {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String RESOLVE_SQL = """
            SELECT mo.id              AS offer_id,
                   mo.marketplace_connection_id AS connection_id,
                   mc.workspace_id     AS workspace_id,
                   mc.marketplace_type AS marketplace_type,
                   mo.marketplace_sku  AS marketplace_sku,
                   mo.marketplace_sku_alt AS marketplace_sku_alt,
                   mc.secret_reference_id AS secret_reference_id
            FROM marketplace_offer mo
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            WHERE mo.id = :offerId
            """;

    public Optional<OfferConnectionRow> resolve(long offerId) {
        var params = new MapSqlParameterSource("offerId", offerId);
        var results = jdbc.query(RESOLVE_SQL, params, (rs, rowNum) -> new OfferConnectionRow(
                rs.getLong("offer_id"),
                rs.getLong("connection_id"),
                rs.getLong("workspace_id"),
                MarketplaceType.valueOf(rs.getString("marketplace_type")),
                rs.getString("marketplace_sku"),
                rs.getString("marketplace_sku_alt"),
                rs.getLong("secret_reference_id")
        ));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public record OfferConnectionRow(
            long offerId,
            long connectionId,
            long workspaceId,
            MarketplaceType marketplaceType,
            String marketplaceSku,
            String marketplaceSkuAlt,
            long secretReferenceId
    ) {
    }
}
