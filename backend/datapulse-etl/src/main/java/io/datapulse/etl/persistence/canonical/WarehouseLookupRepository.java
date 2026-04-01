package io.datapulse.etl.persistence.canonical;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only lookup for resolving external warehouse identifiers to {@code warehouse.id}.
 * Used by canonical finance normalizer and stock sources.
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

    private static final String FIND_ALL_BY_CONNECTION = """
            SELECT external_warehouse_id, id
            FROM warehouse
            WHERE marketplace_connection_id = ?
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

    /**
     * Batch lookup: returns external_warehouse_id → warehouse.id for a connection.
     * Used by stock sources to resolve warehouse_id before upsert.
     */
    public Map<String, Long> findAllIdsByConnection(long connectionId) {
        return jdbc.query(FIND_ALL_BY_CONNECTION,
                        (rs, rowNum) -> Map.entry(
                                rs.getString("external_warehouse_id"),
                                rs.getLong("id")),
                        connectionId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (existing, replacement) -> existing));
    }
}
