package io.datapulse.etl.persistence.canonical;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SellerSkuReadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FIND_BY_SKU_CODE_AND_WORKSPACE = """
            SELECT ss.id, ss.product_master_id, ss.sku_code, ss.barcode
            FROM seller_sku ss
            JOIN product_master pm ON ss.product_master_id = pm.id
            WHERE ss.sku_code = :skuCode
              AND pm.workspace_id = :workspaceId
            LIMIT 1
            """;

    private static final String FIND_SKU_CODE_BY_ID = """
            SELECT sku_code FROM seller_sku WHERE id = :id
            """;

    public Optional<SellerSkuEntity> findBySkuCodeAndWorkspaceId(String skuCode, long workspaceId) {
        var rows = jdbc.query(FIND_BY_SKU_CODE_AND_WORKSPACE,
                Map.of("skuCode", skuCode, "workspaceId", workspaceId),
                (rs, rowNum) -> {
                    var e = new SellerSkuEntity();
                    e.setId(rs.getLong("id"));
                    e.setProductMasterId(rs.getLong("product_master_id"));
                    e.setSkuCode(rs.getString("sku_code"));
                    e.setBarcode(rs.getString("barcode"));
                    return e;
                });
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<String> findSkuCodeById(long id) {
        var rows = jdbc.queryForList(FIND_SKU_CODE_BY_ID, Map.of("id", id), String.class);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
