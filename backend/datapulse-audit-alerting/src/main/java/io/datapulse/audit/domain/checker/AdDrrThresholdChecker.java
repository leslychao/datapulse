package io.datapulse.audit.domain.checker;

import java.math.BigDecimal;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class AdDrrThresholdChecker implements AlertChecker {

    private final AlertClickHouseJdbc clickHouseJdbc;
    private final NamedParameterJdbcTemplate pgJdbc;
    private final AlertEventService alertEventService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    private static final BigDecimal DEFAULT_DRR_THRESHOLD = new BigDecimal("15.0");
    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    private static final String CONNECTION_IDS_SQL = """
        SELECT id FROM marketplace_connection
        WHERE workspace_id = :workspaceId AND status = 'ACTIVE'
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
                dp.marketplace_sku,
                sum(ff.revenue_amount) AS total_revenue
            FROM fact_finance AS ff
            INNER JOIN dim_product AS dp
                ON ff.seller_sku_id = dp.seller_sku_id
            WHERE ff.finance_date >= today() - :lookbackDays
              AND ff.attribution_level IN ('POSTING', 'PRODUCT')
              AND ff.connection_id IN (:connectionIds)
            GROUP BY dp.marketplace_sku
        ) AS ff_rev
            ON fa.marketplace_sku = ff_rev.marketplace_sku
        WHERE fa.ad_date >= today() - :lookbackDays
          AND fa.connection_id IN (:connectionIds)
        GROUP BY fa.connection_id, fa.marketplace_sku, ff_rev.total_revenue
        HAVING sum(fa.spend) > 0
        SETTINGS final = 1
        """;

    @Override
    public String ruleType() {
        return AlertRuleType.AD_DRR_THRESHOLD.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        Map<String, Object> config = parseConfig(rule.config());
        BigDecimal drrThreshold = getBigDecimal(config, "drr_threshold_pct", DEFAULT_DRR_THRESHOLD);
        int lookbackDays = getInt(config, "drr_lookback_days", DEFAULT_LOOKBACK_DAYS);

        List<Long> connectionIds = findActiveConnectionIds(rule.workspaceId());
        if (connectionIds.isEmpty()) {
            return;
        }

        var chParams = new MapSqlParameterSource()
            .addValue("connectionIds", connectionIds)
            .addValue("lookbackDays", lookbackDays);

        List<Map<String, Object>> rows;
        try {
            rows = clickHouseJdbc.ch().queryForList(DRR_BY_SKU_SQL, chParams);
        } catch (Exception e) {
            log.debug("AdDrrThresholdChecker skipped (ClickHouse unavailable): error={}", e.getMessage());
            return;
        }

        Set<Long> violatingConnections = rows.stream()
            .filter(row -> {
                BigDecimal drr = toBigDecimal(row.get("drr_pct"));
                return drr != null && drr.compareTo(drrThreshold) > 0;
            })
            .map(row -> ((Number) row.get("connection_id")).longValue())
            .collect(Collectors.toSet());

        for (long connectionId : violatingConnections) {
            boolean alreadyOpen = !alertEventRepository
                .findActiveByRuleAndConnection(rule.id(), connectionId).isEmpty();
            if (!alreadyOpen) {
                List<Map<String, Object>> connectionRows = rows.stream()
                    .filter(r -> ((Number) r.get("connection_id")).longValue() == connectionId)
                    .filter(r -> {
                        BigDecimal drr = toBigDecimal(r.get("drr_pct"));
                        return drr != null && drr.compareTo(drrThreshold) > 0;
                    })
                    .toList();

                String details = buildDetails(connectionRows, drrThreshold, lookbackDays);
                alertEventService.createRuleBasedAlert(
                    rule.id(), rule.workspaceId(), connectionId,
                    ruleType(), rule.severity(),
                    MessageCodes.AD_DRR_THRESHOLD_TITLE,
                    details, rule.blocksAutomation());
            }
        }

        autoResolveCleared(rule, violatingConnections);
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

    private List<Long> findActiveConnectionIds(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return pgJdbc.queryForList(CONNECTION_IDS_SQL, params, Long.class);
    }

    private String buildDetails(List<Map<String, Object>> rows,
                                BigDecimal threshold, int lookbackDays) {
        try {
            List<Map<String, Object>> items = rows.stream()
                .map(row -> Map.<String, Object>of(
                    "marketplace_sku", row.get("marketplace_sku"),
                    "drr_pct", row.get("drr_pct"),
                    "spend", row.get("total_spend"),
                    "revenue", row.get("total_revenue")))
                .toList();
            return objectMapper.writeValueAsString(Map.of(
                "violations", items,
                "threshold_pct", threshold,
                "lookback_days", lookbackDays));
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
        return null;
    }
}
