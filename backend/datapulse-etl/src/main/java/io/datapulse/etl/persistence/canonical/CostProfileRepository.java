package io.datapulse.etl.persistence.canonical;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CostProfileRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String CLOSE_CURRENT_VERSION = """
            UPDATE cost_profile
            SET valid_to = :newValidFrom - INTERVAL '1 day',
                updated_at = now()
            WHERE seller_sku_id = :sellerSkuId
              AND valid_to IS NULL
              AND valid_from < :newValidFrom
            """;

    private static final String UPSERT_VERSION = """
            INSERT INTO cost_profile (seller_sku_id, cost_price, currency, valid_from,
                                      updated_by_user_id, created_at, updated_at)
            VALUES (:sellerSkuId, :costPrice, :currency, :validFrom, :updatedByUserId, now(), now())
            ON CONFLICT (seller_sku_id, valid_from) DO UPDATE SET
                cost_price = EXCLUDED.cost_price,
                currency = EXCLUDED.currency,
                updated_by_user_id = EXCLUDED.updated_by_user_id,
                updated_at = now()
            WHERE (cost_profile.cost_price, cost_profile.currency)
                IS DISTINCT FROM (EXCLUDED.cost_price, EXCLUDED.currency)
            """;

    private static final String FIND_CURRENT_BY_SKU = """
            SELECT id, seller_sku_id, cost_price, currency, valid_from, valid_to,
                   updated_by_user_id, created_at, updated_at
            FROM cost_profile
            WHERE seller_sku_id = :sellerSkuId
              AND valid_to IS NULL
            """;

    private static final String FIND_HISTORY_BY_SKU = """
            SELECT id, seller_sku_id, cost_price, currency, valid_from, valid_to,
                   updated_by_user_id, created_at, updated_at
            FROM cost_profile
            WHERE seller_sku_id = :sellerSkuId
            ORDER BY valid_from DESC
            """;

    public void closeCurrentVersion(long sellerSkuId, LocalDate newValidFrom) {
        jdbc.update(CLOSE_CURRENT_VERSION, Map.of(
                "sellerSkuId", sellerSkuId,
                "newValidFrom", Date.valueOf(newValidFrom)
        ));
    }

    public void upsertVersion(long sellerSkuId, CostProfileEntity entity) {
        jdbc.update(UPSERT_VERSION, Map.of(
                "sellerSkuId", sellerSkuId,
                "costPrice", entity.getCostPrice(),
                "currency", entity.getCurrency(),
                "validFrom", Date.valueOf(entity.getValidFrom()),
                "updatedByUserId", entity.getUpdatedByUserId()
        ));
    }

    public void createVersion(CostProfileEntity entity) {
        closeCurrentVersion(entity.getSellerSkuId(), entity.getValidFrom());
        upsertVersion(entity.getSellerSkuId(), entity);
    }

    public void batchCreateVersions(List<CostProfileEntity> entities) {
        for (CostProfileEntity entity : entities) {
            createVersion(entity);
        }
    }

    public List<CostProfileEntity> findCurrentBySku(long sellerSkuId) {
        return jdbc.query(FIND_CURRENT_BY_SKU, Map.of("sellerSkuId", sellerSkuId),
                (rs, rowNum) -> {
                    var e = new CostProfileEntity();
                    e.setId(rs.getLong("id"));
                    e.setSellerSkuId(rs.getLong("seller_sku_id"));
                    e.setCostPrice(rs.getBigDecimal("cost_price"));
                    e.setCurrency(rs.getString("currency"));
                    e.setValidFrom(rs.getDate("valid_from").toLocalDate());
                    Date validTo = rs.getDate("valid_to");
                    e.setValidTo(validTo != null ? validTo.toLocalDate() : null);
                    e.setUpdatedByUserId(rs.getLong("updated_by_user_id"));
                    return e;
                });
    }

    public List<CostProfileEntity> findHistoryBySku(long sellerSkuId) {
        return jdbc.query(FIND_HISTORY_BY_SKU, Map.of("sellerSkuId", sellerSkuId),
                (rs, rowNum) -> {
                    var e = new CostProfileEntity();
                    e.setId(rs.getLong("id"));
                    e.setSellerSkuId(rs.getLong("seller_sku_id"));
                    e.setCostPrice(rs.getBigDecimal("cost_price"));
                    e.setCurrency(rs.getString("currency"));
                    e.setValidFrom(rs.getDate("valid_from").toLocalDate());
                    Date validTo = rs.getDate("valid_to");
                    e.setValidTo(validTo != null ? validTo.toLocalDate() : null);
                    e.setUpdatedByUserId(rs.getLong("updated_by_user_id"));
                    return e;
                });
    }
}
