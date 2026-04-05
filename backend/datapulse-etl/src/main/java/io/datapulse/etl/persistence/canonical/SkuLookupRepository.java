package io.datapulse.etl.persistence.canonical;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only lookups for resolving provider SKU identifiers to {@code seller_sku.id}.
 * Used by canonical finance normalizer to populate {@code canonical_finance_entry.seller_sku_id}.
 */
@Repository
@RequiredArgsConstructor
public class SkuLookupRepository {

    private final JdbcTemplate jdbc;

    private static final String FIND_BY_MARKETPLACE_SKU = """
            SELECT mo.seller_sku_id
            FROM marketplace_offer mo
            WHERE mo.marketplace_connection_id = ?
              AND (mo.marketplace_sku = ?
                   OR ? = ANY(string_to_array(mo.marketplace_sku_alt, ',')))
            LIMIT 1
            """;

    private static final String FIND_BY_VENDOR_CODE = """
            SELECT ss.id
            FROM seller_sku ss
            JOIN product_master pm ON ss.product_master_id = pm.id
            WHERE pm.workspace_id = ?
              AND pm.external_code = ?
            LIMIT 1
            """;

    private static final String FIND_ALL_BY_WORKSPACE = """
            SELECT ss.sku_code, ss.id
            FROM seller_sku ss
            JOIN product_master pm ON pm.id = ss.product_master_id
            WHERE pm.workspace_id = ?
            """;

    private static final String FIND_ALL_OFFER_IDS_BY_CONNECTION = """
            SELECT marketplace_sku, id
            FROM marketplace_offer
            WHERE marketplace_connection_id = ?
            """;

    /**
     * Batch lookup: returns all seller_sku records for a workspace.
     *
     * @return Map of sku_code → seller_sku.id
     */
    public Map<String, Long> findAllByWorkspace(long workspaceId) {
        return jdbc.query(FIND_ALL_BY_WORKSPACE,
                        (rs, rowNum) -> Map.entry(
                                rs.getString("sku_code"),
                                rs.getLong("id")),
                        workspaceId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Batch lookup: returns marketplace_sku → marketplace_offer.id for a connection.
     * Used by stock/price sources to resolve marketplace_offer_id before upsert.
     */
    public Map<String, Long> findAllOfferIdsByConnection(long connectionId) {
        return jdbc.query(FIND_ALL_OFFER_IDS_BY_CONNECTION,
                        (rs, rowNum) -> Map.entry(
                                rs.getString("marketplace_sku"),
                                rs.getLong("id")),
                        connectionId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (existing, replacement) -> existing));
    }

    /**
     * Primary lookup via marketplace_offer. Works for both WB (nm_id) and Ozon (items[].sku).
     */
    public Optional<Long> findByMarketplaceSku(long connectionId, String marketplaceSku) {
        if (marketplaceSku == null) {
            return Optional.empty();
        }
        var results = jdbc.queryForList(FIND_BY_MARKETPLACE_SKU, Long.class,
                connectionId, marketplaceSku, marketplaceSku);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Fallback lookup via product_master.external_code (vendorCode).
     * Used when marketplace_offer is not yet created but product_master exists.
     */
    public Optional<Long> findByVendorCode(long workspaceId, String vendorCode) {
        if (vendorCode == null) {
            return Optional.empty();
        }
        var results = jdbc.queryForList(FIND_BY_VENDOR_CODE, Long.class,
                workspaceId, vendorCode);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
