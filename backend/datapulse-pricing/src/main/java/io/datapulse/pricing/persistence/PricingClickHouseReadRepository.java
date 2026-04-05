package io.datapulse.pricing.persistence;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries against ClickHouse for pricing signals that require
 * analytical aggregation (ad_cost_ratio, and in the future: avg_commission_pct,
 * avg_logistics_per_unit, return_rate_pct).
 */
@Repository
public class PricingClickHouseReadRepository {

    private final NamedParameterJdbcTemplate ch;

    public PricingClickHouseReadRepository(
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
        this.ch = new NamedParameterJdbcTemplate(clickhouseJdbcTemplate);
    }

    private static final String AD_COST_RATIOS = """
            SELECT
                fa.marketplace_sku,
                sum(fa.spend) / nullIf(sum(ff.revenue_amount), 0) AS ad_cost_ratio
            FROM fact_advertising AS fa
            INNER JOIN dim_product AS dp
                ON fa.connection_id = dp.connection_id
                AND fa.marketplace_sku = dp.marketplace_sku
            LEFT JOIN fact_finance AS ff
                ON dp.connection_id = ff.connection_id
                AND dp.seller_sku_id = ff.seller_sku_id
                AND toYYYYMM(fa.ad_date) = toYYYYMM(ff.finance_date)
            WHERE fa.marketplace_sku IN (:skus)
                AND fa.ad_date >= today() - :lookbackDays
                AND ff.finance_date >= today() - :lookbackDays
                AND ff.attribution_level IN ('POSTING', 'PRODUCT')
            GROUP BY fa.marketplace_sku
            SETTINGS final = 1
            """;

    /**
     * Calculates ad spend / revenue ratio per marketplace SKU over a rolling window.
     *
     * @return marketplace_sku → ad_cost_ratio; SKU with spend but no revenue → null (division by zero);
     *         SKU absent from result → no advertising data (caller treats as 0)
     */
    public Map<String, BigDecimal> findAdCostRatios(List<String> marketplaceSkus,
                                                    int lookbackDays) {
        if (marketplaceSkus.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource()
                .addValue("skus", marketplaceSkus)
                .addValue("lookbackDays", lookbackDays);
        return ch.query(AD_COST_RATIOS, params, rs -> {
            Map<String, BigDecimal> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("marketplace_sku"),
                        rs.getBigDecimal("ad_cost_ratio"));
            }
            return result;
        });
    }
}
