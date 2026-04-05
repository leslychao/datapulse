package io.datapulse.etl.persistence.canonical;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CostProfileRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "skuCode", "ss.sku_code",
            "productName", "COALESCE(pm.name, '')",
            "costPrice", "cp.cost_price",
            "updatedAt", "cp.updated_at");

    private static final String CLOSE_CURRENT_VERSION = """
            UPDATE cost_profile
            SET valid_to = :closedValidTo,
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
            SELECT cp.id, cp.seller_sku_id, cp.cost_price, cp.currency,
                   cp.valid_from, cp.valid_to, cp.updated_by_user_id,
                   cp.created_at, cp.updated_at
            FROM cost_profile cp
            WHERE cp.seller_sku_id = :sellerSkuId
              AND cp.valid_to IS NULL
            """;

    private static final String FIND_HISTORY_BY_SKU = """
            SELECT cp.id, cp.seller_sku_id, cp.cost_price, cp.currency,
                   cp.valid_from, cp.valid_to, cp.updated_by_user_id,
                   cp.created_at, cp.updated_at
            FROM cost_profile cp
            WHERE cp.seller_sku_id = :sellerSkuId
            ORDER BY cp.valid_from DESC
            """;

    private static final String BASE_CURRENT_PROFILES = """
            FROM cost_profile cp
            JOIN seller_sku ss ON cp.seller_sku_id = ss.id
            JOIN product_master pm ON ss.product_master_id = pm.id
            WHERE cp.valid_to IS NULL
              AND pm.workspace_id = :workspaceId
            """;

    private static final String FIND_BY_ID_AND_WORKSPACE = """
            SELECT cp.id, cp.seller_sku_id, ss.sku_code,
                   COALESCE(pm.name, '') AS product_name,
                   cp.cost_price, cp.currency,
                   cp.valid_from, cp.valid_to, cp.updated_by_user_id,
                   cp.created_at, cp.updated_at
            FROM cost_profile cp
            JOIN seller_sku ss ON cp.seller_sku_id = ss.id
            JOIN product_master pm ON ss.product_master_id = pm.id
            WHERE cp.id = :id
              AND pm.workspace_id = :workspaceId
            """;

    private static final String UPDATE_PROFILE = """
            UPDATE cost_profile
            SET cost_price = :costPrice,
                currency = :currency,
                valid_from = :validFrom,
                updated_by_user_id = :updatedByUserId,
                updated_at = now()
            WHERE id = :id
            """;

    private static final String DELETE_BY_ID = """
            DELETE FROM cost_profile WHERE id = :id
            """;

    public void closeCurrentVersion(long sellerSkuId, LocalDate newValidFrom) {
        LocalDate closedValidTo = newValidFrom.minusDays(1);
        jdbc.update(CLOSE_CURRENT_VERSION, Map.of(
                "sellerSkuId", sellerSkuId,
                "newValidFrom", Date.valueOf(newValidFrom),
                "closedValidTo", Date.valueOf(closedValidTo)));
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

    public Optional<CostProfileRow> findByIdAndWorkspaceId(long id, long workspaceId) {
        var rows = jdbc.query(FIND_BY_ID_AND_WORKSPACE,
                Map.of("id", id, "workspaceId", workspaceId), this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void updateProfile(long id, BigDecimal costPrice, String currency,
                              LocalDate validFrom, long updatedByUserId) {
        jdbc.update(UPDATE_PROFILE, Map.of(
                "id", id,
                "costPrice", costPrice,
                "currency", currency,
                "validFrom", Date.valueOf(validFrom),
                "updatedByUserId", updatedByUserId));
    }

    public void deleteById(long id) {
        jdbc.update(DELETE_BY_ID, Map.of("id", id));
    }

    private static final String FIND_CURRENT_PROFILE_ID = """
            SELECT cp.id
            FROM cost_profile cp
            JOIN seller_sku ss ON ss.id = cp.seller_sku_id
            WHERE cp.seller_sku_id = :sellerSkuId
              AND ss.workspace_id = :workspaceId
              AND cp.valid_to IS NULL
            LIMIT 1
            """;

    public Optional<Long> findCurrentProfileId(long sellerSkuId, long workspaceId) {
        var rows = jdbc.queryForList(FIND_CURRENT_PROFILE_ID,
                Map.of("sellerSkuId", sellerSkuId, "workspaceId", workspaceId),
                Long.class);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<CostProfileEntity> findCurrentBySku(long sellerSkuId) {
        return jdbc.query(FIND_CURRENT_BY_SKU, Map.of("sellerSkuId", sellerSkuId), this::mapEntity);
    }

    public List<CostProfileEntity> findHistoryBySku(long sellerSkuId) {
        return jdbc.query(FIND_HISTORY_BY_SKU, Map.of("sellerSkuId", sellerSkuId), this::mapEntity);
    }

    public List<CostProfileRow> findCurrentProfiles(long workspaceId, Long sellerSkuId,
                                                    String search, Sort sort, int limit, long offset) {
        var params = buildProfileFilterParams(workspaceId, sellerSkuId, search);
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String sql = """
                SELECT cp.id, cp.seller_sku_id, ss.sku_code,
                       COALESCE(pm.name, '') AS product_name,
                       cp.cost_price, cp.currency,
                       cp.valid_from, cp.valid_to, cp.updated_by_user_id,
                       cp.created_at, cp.updated_at
                """ + BASE_CURRENT_PROFILES
                + buildProfileFilterClause(sellerSkuId, search)
                + buildOrderByClause(sort) + """
                LIMIT :limit OFFSET :offset
                """;

        return jdbc.query(sql, params, this::mapRow);
    }

    public long countCurrentProfiles(long workspaceId, Long sellerSkuId, String search) {
        var params = buildProfileFilterParams(workspaceId, sellerSkuId, search);

        String sql = "SELECT count(*) " + BASE_CURRENT_PROFILES
                + buildProfileFilterClause(sellerSkuId, search);

        return jdbc.queryForObject(sql, params, Long.class);
    }

    /**
     * All current cost profiles for a workspace, ordered by SKU (CSV export / round-trip with bulk import).
     */
    public List<CostProfileRow> findAllCurrentProfilesForExport(long workspaceId) {
        String sql = """
                SELECT cp.id, cp.seller_sku_id, ss.sku_code,
                       COALESCE(pm.name, '') AS product_name,
                       cp.cost_price, cp.currency,
                       cp.valid_from, cp.valid_to, cp.updated_by_user_id,
                       cp.created_at, cp.updated_at
                """ + BASE_CURRENT_PROFILES + """
                ORDER BY ss.sku_code
                """;
        return jdbc.query(sql, Map.of("workspaceId", workspaceId), this::mapRow);
    }

    private MapSqlParameterSource buildProfileFilterParams(long workspaceId, Long sellerSkuId,
                                                           String search) {
        var params = new MapSqlParameterSource().addValue("workspaceId", workspaceId);
        if (sellerSkuId != null) {
            params.addValue("sellerSkuId", sellerSkuId);
        }
        if (search != null && !search.isBlank()) {
            params.addValue("search", "%" + search.trim() + "%");
        }
        return params;
    }

    private String buildProfileFilterClause(Long sellerSkuId, String search) {
        var sb = new StringBuilder();
        if (sellerSkuId != null) {
            sb.append(" AND cp.seller_sku_id = :sellerSkuId");
        }
        if (search != null && !search.isBlank()) {
            sb.append(" AND (ss.sku_code ILIKE :search OR COALESCE(pm.name, '') ILIKE :search)");
        }
        sb.append('\n');
        return sb.toString();
    }

    private String buildOrderByClause(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return " ORDER BY ss.sku_code ASC NULLS LAST ";
        }
        var sb = new StringBuilder(" ORDER BY ");
        var orders = sort.stream().toList();
        for (int i = 0; i < orders.size(); i++) {
            Sort.Order order = orders.get(i);
            String column = SORT_WHITELIST.getOrDefault(order.getProperty(), "ss.sku_code");
            sb.append(column).append(' ').append(order.getDirection().name()).append(" NULLS LAST");
            if (i < orders.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(' ');
        return sb.toString();
    }

    private CostProfileEntity mapEntity(ResultSet rs, int rowNum) throws SQLException {
        var e = new CostProfileEntity();
        e.setId(rs.getLong("id"));
        e.setSellerSkuId(rs.getLong("seller_sku_id"));
        e.setCostPrice(rs.getBigDecimal("cost_price"));
        e.setCurrency(rs.getString("currency"));
        e.setValidFrom(rs.getDate("valid_from").toLocalDate());
        Date validTo = rs.getDate("valid_to");
        e.setValidTo(validTo != null ? validTo.toLocalDate() : null);
        e.setUpdatedByUserId(rs.getLong("updated_by_user_id"));
        e.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        e.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return e;
    }

    private CostProfileRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Date validTo = rs.getDate("valid_to");
        return CostProfileRow.builder()
                .id(rs.getLong("id"))
                .sellerSkuId(rs.getLong("seller_sku_id"))
                .skuCode(rs.getString("sku_code"))
                .productName(rs.getString("product_name"))
                .costPrice(rs.getBigDecimal("cost_price"))
                .currency(rs.getString("currency"))
                .validFrom(rs.getDate("valid_from").toLocalDate())
                .validTo(validTo != null ? validTo.toLocalDate() : null)
                .updatedByUserId(rs.getLong("updated_by_user_id"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
                .build();
    }
}
