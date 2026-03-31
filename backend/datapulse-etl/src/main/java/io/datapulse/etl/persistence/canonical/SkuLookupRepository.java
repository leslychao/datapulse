package io.datapulse.etl.persistence.canonical;

import java.util.Optional;

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
              AND (mo.marketplace_sku = ? OR mo.marketplace_sku_alt = ?)
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
