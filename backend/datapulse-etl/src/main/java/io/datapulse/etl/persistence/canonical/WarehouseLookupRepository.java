package io.datapulse.etl.persistence.canonical;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only lookup for resolving external warehouse identifiers to {@code warehouse.id}.
 * Used by canonical finance normalizer to populate {@code canonical_finance_entry.warehouse_id}.
 */
@Repository
@RequiredArgsConstructor
public class WarehouseLookupRepository {

    private final JdbcTemplate jdbc;

    private static final String FIND_BY_EXTERNAL_ID = """
            SELECT id
            FROM warehouse
            WHERE marketplace_connection_id = ?
              AND external_warehouse_id = ?
            LIMIT 1
            """;

    /**
     * Resolves WB ppvz_office_id or Ozon posting.warehouse_id to the internal warehouse PK.
     */
    public Optional<Long> findByExternalId(long connectionId, String externalWarehouseId) {
        if (externalWarehouseId == null) {
            return Optional.empty();
        }
        var results = jdbc.queryForList(FIND_BY_EXTERNAL_ID, Long.class,
                connectionId, externalWarehouseId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
