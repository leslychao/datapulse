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

/**
 * Detects advertising campaigns with low CTR or CR despite significant spend.
 * Source: ClickHouse only (fact_advertising aggregated over 7 days).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdInefficientCampaignChecker implements AlertChecker {

    private final AlertClickHouseJdbc clickHouseJdbc;
    private final NamedParameterJdbcTemplate pgJdbc;
    private final AlertEventService alertEventService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    private static final BigDecimal DEFAULT_MIN_SPEND = new BigDecimal("1000.0");
    private static final BigDecimal DEFAULT_CTR_THRESHOLD = new BigDecimal("1.0");
    private static final BigDecimal DEFAULT_CR_THRESHOLD = new BigDecimal("2.0");
    private static final int LOOKBACK_DAYS = 7;

    private static final String CONNECTION_IDS_SQL = """
        SELECT id FROM marketplace_connection
        WHERE workspace_id = :workspaceId AND status = 'ACTIVE'
        """;

    private static final String INEFFICIENT_CAMPAIGNS_SQL = """
        SELECT
            connection_id,
            campaign_id,
            sum(spend) AS total_spend,
            sum(views) AS total_views,
            sum(clicks) AS total_clicks,
            sum(orders) AS total_orders,
            if(sum(views) > 0,
               sum(clicks) / sum(views) * 100,
               0) AS ctr_pct,
            if(sum(clicks) > 0,
               sum(orders) / sum(clicks) * 100,
               0) AS cr_pct
        FROM fact_advertising
        WHERE connection_id IN (:connectionIds)
          AND ad_date >= today() - :lookbackDays
        GROUP BY connection_id, campaign_id
        HAVING sum(spend) >= :minSpend
        SETTINGS final = 1
        """;

    @Override
    public String ruleType() {
        return AlertRuleType.AD_INEFFICIENT_CAMPAIGN.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        Map<String, Object> config = parseConfig(rule.config());
        BigDecimal minSpend = getBigDecimal(config, "inefficient_min_spend", DEFAULT_MIN_SPEND);
        BigDecimal ctrThreshold = getBigDecimal(config, "inefficient_ctr_threshold", DEFAULT_CTR_THRESHOLD);
        BigDecimal crThreshold = getBigDecimal(config, "inefficient_cr_threshold", DEFAULT_CR_THRESHOLD);

        List<Long> connectionIds = findActiveConnectionIds(rule.workspaceId());
        if (connectionIds.isEmpty()) {
            return;
        }

        var chParams = new MapSqlParameterSource()
            .addValue("connectionIds", connectionIds)
            .addValue("lookbackDays", LOOKBACK_DAYS)
            .addValue("minSpend", minSpend);

        List<Map<String, Object>> rows;
        try {
            rows = clickHouseJdbc.ch().queryForList(INEFFICIENT_CAMPAIGNS_SQL, chParams);
        } catch (Exception e) {
            log.debug("AdInefficientCampaignChecker skipped (ClickHouse unavailable): error={}",
                e.getMessage());
            return;
        }

        Map<Long, List<Map<String, Object>>> violationsByConnection = rows.stream()
            .filter(row -> {
                BigDecimal ctr = toBigDecimal(row.get("ctr_pct"));
                BigDecimal cr = toBigDecimal(row.get("cr_pct"));
                return (ctr != null && ctr.compareTo(ctrThreshold) < 0)
                    || (cr != null && cr.compareTo(crThreshold) < 0);
            })
            .collect(Collectors.groupingBy(
                row -> ((Number) row.get("connection_id")).longValue()));

        for (var entry : violationsByConnection.entrySet()) {
            long connectionId = entry.getKey();
            List<Map<String, Object>> violations = entry.getValue();

            boolean alreadyOpen = !alertEventRepository
                .findActiveByRuleAndConnection(rule.id(), connectionId).isEmpty();
            if (!alreadyOpen) {
                String details = buildDetails(violations, ctrThreshold, crThreshold);
                alertEventService.createRuleBasedAlert(
                    rule.id(), rule.workspaceId(), connectionId,
                    ruleType(), rule.severity(),
                    MessageCodes.AD_INEFFICIENT_CAMPAIGN_TITLE,
                    details, rule.blocksAutomation());
            }
        }

        autoResolveCleared(rule, violationsByConnection.keySet());
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

    private String buildDetails(List<Map<String, Object>> violations,
                                BigDecimal ctrThreshold, BigDecimal crThreshold) {
        try {
            List<Map<String, Object>> items = violations.stream()
                .map(row -> Map.<String, Object>of(
                    "campaign_id", row.get("campaign_id"),
                    "spend", row.get("total_spend"),
                    "views", row.get("total_views"),
                    "clicks", row.get("total_clicks"),
                    "orders", row.get("total_orders"),
                    "ctr_pct", row.get("ctr_pct"),
                    "cr_pct", row.get("cr_pct")))
                .toList();
            return objectMapper.writeValueAsString(Map.of(
                "inefficient_campaigns", items,
                "ctr_threshold_pct", ctrThreshold,
                "cr_threshold_pct", crThreshold));
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
