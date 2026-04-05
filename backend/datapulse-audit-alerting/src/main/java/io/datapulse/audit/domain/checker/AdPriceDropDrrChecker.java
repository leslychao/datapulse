package io.datapulse.audit.domain.checker;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.config.AlertClickHouseJdbc;
import io.datapulse.audit.domain.AlertEventService;
import io.datapulse.audit.domain.AlertRuleType;
import io.datapulse.audit.persistence.AlertEventRepository;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Detects price decreases on products with high DRR (advertising cost ratio).
 * Cross-store: PostgreSQL (price_decision) + ClickHouse (fact_advertising + fact_finance).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdPriceDropDrrChecker implements AlertChecker {

    private final NamedParameterJdbcTemplate pgJdbc;
    private final AlertClickHouseJdbc clickHouseJdbc;
    private final AlertEventService alertEventService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    private static final BigDecimal DEFAULT_DRR_THRESHOLD = new BigDecimal("15.0");
    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    /**
     * Recent price decreases: decision_type = CHANGE, target_price < current_price,
     * created within last 24 hours. Joins marketplace_offer to get marketplace_sku.
     */
    private static final String PRICE_DROP_SQL = """
        SELECT
            pd.marketplace_offer_id,
            pd.current_price,
            pd.target_price,
            pd.price_change_pct,
            mo.marketplace_sku,
            mo.marketplace_connection_id AS connection_id
        FROM price_decision pd
        JOIN marketplace_offer mo ON mo.id = pd.marketplace_offer_id
        JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
        WHERE pd.workspace_id = :workspaceId
          AND pd.decision_type = 'CHANGE'
          AND pd.target_price < pd.current_price
          AND pd.created_at >= now() - INTERVAL '24 hours'
          AND mc.status = 'ACTIVE'
        """;

    private static final String DRR_BY_SKU_SQL = """
        SELECT
            fa.connection_id,
            fa.marketplace_sku,
            sum(fa.spend) AS total_spend,
            coalesce(ff_rev.total_revenue, toDecimal64(0, 2)) AS total_revenue,
            if(ff_rev.total_revenue > 0,
               sum(fa.spend) / ff_rev.total_revenue * 100,
               toDecimal64(0, 2)) AS drr_pct
        FROM fact_advertising AS fa
        LEFT JOIN (
            SELECT
                ff.connection_id,
                dp.marketplace_sku,
                sum(ff.revenue_amount) AS total_revenue
            FROM fact_finance AS ff
            INNER JOIN dim_product AS dp
                ON ff.connection_id = dp.connection_id
                AND ff.seller_sku_id = dp.seller_sku_id
            WHERE ff.finance_date >= today() - :lookbackDays
              AND ff.attribution_level IN ('POSTING', 'PRODUCT')
              AND ff.connection_id IN (:connectionIds)
            GROUP BY ff.connection_id, dp.marketplace_sku
        ) AS ff_rev
            ON fa.connection_id = ff_rev.connection_id
            AND fa.marketplace_sku = ff_rev.marketplace_sku
        WHERE fa.ad_date >= today() - :lookbackDays
          AND fa.connection_id IN (:connectionIds)
          AND fa.marketplace_sku IN (:skus)
        GROUP BY fa.connection_id, fa.marketplace_sku, ff_rev.total_revenue
        HAVING sum(fa.spend) > 0
        SETTINGS final = 1
        """;

    @Override
    public String ruleType() {
        return AlertRuleType.AD_PRICE_DROP_HIGH_DRR.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        Map<String, Object> config = parseConfig(rule.config());
        BigDecimal drrThreshold = getBigDecimal(config, "drr_threshold_pct", DEFAULT_DRR_THRESHOLD);
        int lookbackDays = getInt(config, "drr_lookback_days", DEFAULT_LOOKBACK_DAYS);

        var pgParams = new MapSqlParameterSource("workspaceId", rule.workspaceId());
        List<Map<String, Object>> priceDrops = pgJdbc.queryForList(PRICE_DROP_SQL, pgParams);

        if (priceDrops.isEmpty()) {
            autoResolveAll(rule);
            return;
        }

        Set<Long> connectionIds = priceDrops.stream()
            .map(row -> ((Number) row.get("connection_id")).longValue())
            .collect(Collectors.toSet());
        List<String> skus = priceDrops.stream()
            .map(row -> (String) row.get("marketplace_sku"))
            .filter(sku -> sku != null)
            .distinct()
            .toList();

        Map<String, BigDecimal> drrByKey = fetchDrr(connectionIds, skus, lookbackDays);

        Map<Long, List<Map<String, Object>>> violationsByConnection = priceDrops.stream()
            .filter(row -> {
                long connId = ((Number) row.get("connection_id")).longValue();
                String sku = (String) row.get("marketplace_sku");
                if (sku == null) {
                    return false;
                }
                BigDecimal drr = drrByKey.get(connId + ":" + sku);
                return drr != null && drr.compareTo(drrThreshold) > 0;
            })
            .collect(Collectors.groupingBy(
                row -> ((Number) row.get("connection_id")).longValue()));

        for (var entry : violationsByConnection.entrySet()) {
            long connectionId = entry.getKey();
            List<Map<String, Object>> violations = entry.getValue();

            boolean alreadyOpen = !alertEventRepository
                .findActiveByRuleAndConnection(rule.id(), connectionId).isEmpty();
            if (!alreadyOpen) {
                String details = buildDetails(violations, drrByKey, drrThreshold);
                alertEventService.createRuleBasedAlert(
                    rule.id(), rule.workspaceId(), connectionId,
                    ruleType(), rule.severity(),
                    MessageCodes.AD_PRICE_DROP_HIGH_DRR_TITLE,
                    details, rule.blocksAutomation());
            }
        }

        autoResolveCleared(rule, violationsByConnection.keySet());
    }

    private Map<String, BigDecimal> fetchDrr(Set<Long> connectionIds,
                                             List<String> skus, int lookbackDays) {
        if (connectionIds.isEmpty() || skus.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            var chParams = new MapSqlParameterSource()
                .addValue("connectionIds", connectionIds)
                .addValue("skus", skus)
                .addValue("lookbackDays", lookbackDays);
            List<Map<String, Object>> rows = clickHouseJdbc.ch()
                .queryForList(DRR_BY_SKU_SQL, chParams);
            return rows.stream()
                .collect(Collectors.toMap(
                    row -> ((Number) row.get("connection_id")).longValue()
                        + ":" + row.get("marketplace_sku"),
                    row -> toBigDecimal(row.get("drr_pct")),
                    (a, b) -> b));
        } catch (Exception e) {
            log.debug("AdPriceDropDrrChecker: ClickHouse DRR fetch failed: error={}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void autoResolveAll(AlertRuleResponse rule) {
        alertEventRepository.findActiveByRule(rule.id()).stream()
            .filter(event -> event.connectionId() != null)
            .map(event -> event.connectionId())
            .distinct()
            .forEach(connectionId ->
                alertEventService.autoResolve(rule.id(), connectionId, rule.workspaceId()));
    }

    private void autoResolveCleared(AlertRuleResponse rule, Set<Long> currentViolations) {
        alertEventRepository.findActiveByRule(rule.id()).stream()
            .filter(event -> event.connectionId() != null)
            .map(event -> event.connectionId())
            .distinct()
            .filter(connectionId -> !currentViolations.contains(connectionId))
            .forEach(connectionId ->
                alertEventService.autoResolve(rule.id(), connectionId, rule.workspaceId()));
    }

    private String buildDetails(List<Map<String, Object>> violations,
                                Map<String, BigDecimal> drrByKey, BigDecimal threshold) {
        try {
            List<Map<String, Object>> items = violations.stream()
                .map(row -> {
                    long connId = ((Number) row.get("connection_id")).longValue();
                    String sku = (String) row.get("marketplace_sku");
                    BigDecimal drr = drrByKey.getOrDefault(connId + ":" + sku, BigDecimal.ZERO);
                    return Map.<String, Object>of(
                        "marketplace_sku", sku,
                        "current_price", row.get("current_price"),
                        "target_price", row.get("target_price"),
                        "price_change_pct", row.get("price_change_pct"),
                        "drr_pct", drr);
                })
                .toList();
            return objectMapper.writeValueAsString(Map.of(
                "price_drop_violations", items,
                "drr_threshold_pct", threshold));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked") // safe: config is always Map<String, Object> from JSON parsing
    private Map<String, Object> parseConfig(Object config) {
        if (config instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (config instanceof String configJson) {
            try {
                return objectMapper.readValue(configJson, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse alert rule config, using defaults: error={}", e.getMessage());
            }
        }
        return Map.of();
    }

    private BigDecimal getBigDecimal(Map<String, Object> config, String key, BigDecimal defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return defaultValue;
    }

    private int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
