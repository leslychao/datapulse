package io.datapulse.etl.persistence.canonical;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PromoCampaignLookupRepository {

    private final JdbcTemplate jdbc;

    private static final String FIND_BY_EXTERNAL_PROMO_ID = """
            SELECT id
            FROM canonical_promo_campaign
            WHERE connection_id = ?
              AND external_promo_id = ?
            LIMIT 1
            """;

    private static final String FIND_ALL_BY_CONNECTION = """
            SELECT external_promo_id, id
            FROM canonical_promo_campaign
            WHERE connection_id = ?
            """;

    public Optional<Long> findByExternalPromoId(long connectionId, String externalPromoId) {
        if (externalPromoId == null) {
            return Optional.empty();
        }
        var results = jdbc.queryForList(FIND_BY_EXTERNAL_PROMO_ID, Long.class,
                connectionId, externalPromoId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Map<String, Long> findAllByConnection(long connectionId) {
        return jdbc.query(FIND_ALL_BY_CONNECTION,
                        (rs, rowNum) -> Map.entry(
                                rs.getString("external_promo_id"),
                                rs.getLong("id")),
                        connectionId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
