package io.datapulse.etl.persistence.canonical;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MarketplaceOfferLookupRepository {

    private final JdbcTemplate jdbc;

    private static final String FIND_BY_MARKETPLACE_SKU = """
            SELECT id
            FROM marketplace_offer
            WHERE marketplace_connection_id = ?
              AND (marketplace_sku = ? OR marketplace_sku_alt = ?)
            LIMIT 1
            """;

    public Optional<Long> findByMarketplaceSku(long connectionId, String marketplaceSku) {
        if (marketplaceSku == null) {
            return Optional.empty();
        }
        var results = jdbc.queryForList(FIND_BY_MARKETPLACE_SKU, Long.class,
                connectionId, marketplaceSku, marketplaceSku);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
