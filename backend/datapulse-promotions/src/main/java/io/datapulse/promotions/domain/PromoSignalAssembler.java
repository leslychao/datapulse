package io.datapulse.promotions.domain;

import io.datapulse.promotions.persistence.PromoProductRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoSignalAssembler {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String AVG_COMMISSION_AND_LOGISTICS = """
            SELECT mo.id AS marketplace_offer_id,
                   ff.avg_commission_pct,
                   ff.avg_logistics_per_unit
            FROM marketplace_offer mo
            JOIN LATERAL (
                SELECT AVG(ff2.commission_amount / NULLIF(ff2.sale_amount, 0)) AS avg_commission_pct,
                       AVG(ff2.logistics_amount / NULLIF(ff2.quantity, 0)) AS avg_logistics_per_unit
                FROM fact_finance ff2
                WHERE ff2.marketplace_offer_id = mo.id
                  AND ff2.operation_date >= CURRENT_DATE - INTERVAL '90 days'
                  AND ff2.sale_amount > 0
            ) ff ON true
            WHERE mo.id IN (:offerIds)
            """;

    private static final String AVG_DAILY_VELOCITY = """
            SELECT marketplace_offer_id,
                   COALESCE(SUM(quantity), 0)::numeric / GREATEST(COUNT(DISTINCT sale_date), 1) AS avg_daily_velocity
            FROM fact_sales
            WHERE marketplace_offer_id IN (:offerIds)
              AND sale_date >= CURRENT_DATE - INTERVAL '30 days'
            GROUP BY marketplace_offer_id
            """;

    public List<PromoProductRow> enrichWithSignals(List<PromoProductRow> products) {
        if (products.isEmpty()) {
            return products;
        }

        List<Long> offerIds = products.stream()
                .map(PromoProductRow::marketplaceOfferId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, CostSignals> costSignals = loadCostSignals(offerIds);
        Map<Long, BigDecimal> velocities = loadDailyVelocity(offerIds);

        return products.stream()
                .map(p -> {
                    CostSignals cs = costSignals.get(p.marketplaceOfferId());
                    BigDecimal effectiveCostRate = computeEffectiveCostRate(
                            cs, p.requiredPrice());
                    BigDecimal velocity = velocities.get(p.marketplaceOfferId());

                    return new PromoProductRow(
                            p.promoProductId(),
                            p.campaignId(),
                            p.marketplaceOfferId(),
                            p.categoryId(),
                            p.participationStatus(),
                            p.requiredPrice(),
                            p.currentPrice(),
                            p.cogs(),
                            effectiveCostRate,
                            p.stockAvailable(),
                            velocity,
                            p.dateFrom(),
                            p.dateTo(),
                            p.freezeAt());
                })
                .collect(Collectors.toList());
    }

    private Map<Long, CostSignals> loadCostSignals(List<Long> offerIds) {
        try {
            var params = new MapSqlParameterSource("offerIds", offerIds);
            Map<Long, CostSignals> result = new HashMap<>();
            jdbcTemplate.query(AVG_COMMISSION_AND_LOGISTICS, params, rs -> {
                while (rs.next()) {
                    result.put(rs.getLong("marketplace_offer_id"),
                            new CostSignals(
                                    rs.getBigDecimal("avg_commission_pct"),
                                    rs.getBigDecimal("avg_logistics_per_unit")));
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("Failed to load cost signals from fact_finance, proceeding without: {}",
                    e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<Long, BigDecimal> loadDailyVelocity(List<Long> offerIds) {
        try {
            var params = new MapSqlParameterSource("offerIds", offerIds);
            Map<Long, BigDecimal> result = new HashMap<>();
            jdbcTemplate.query(AVG_DAILY_VELOCITY, params, rs -> {
                while (rs.next()) {
                    result.put(rs.getLong("marketplace_offer_id"),
                            rs.getBigDecimal("avg_daily_velocity"));
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("Failed to load daily velocity from fact_sales, proceeding without: {}",
                    e.getMessage());
            return Collections.emptyMap();
        }
    }

    private BigDecimal computeEffectiveCostRate(CostSignals cs, BigDecimal promoPrice) {
        if (cs == null) {
            return null;
        }

        BigDecimal commPct = cs.avgCommissionPct() != null ? cs.avgCommissionPct() : BigDecimal.ZERO;
        BigDecimal logisticsPerUnit = cs.avgLogisticsPerUnit() != null
                ? cs.avgLogisticsPerUnit() : BigDecimal.ZERO;

        BigDecimal logisticsRate = BigDecimal.ZERO;
        if (promoPrice != null && promoPrice.compareTo(BigDecimal.ZERO) > 0) {
            logisticsRate = logisticsPerUnit.divide(promoPrice, 4, RoundingMode.HALF_UP);
        }

        return commPct.add(logisticsRate);
    }

    private record CostSignals(BigDecimal avgCommissionPct, BigDecimal avgLogisticsPerUnit) {}
}
