package io.datapulse.audit.domain.checker;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * Detects active advertising campaigns spending budget on products with zero stock.
 * Cross-store: PostgreSQL (campaigns + stock) + ClickHouse (spend > 0 today).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdNoStockChecker implements AlertChecker {

    private final NamedParameterJdbcTemplate pgJdbc;
    private final AlertClickHouseJdbc clickHouseJdbc;
    private final AlertEventService alertEventService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    private static final String ZERO_STOCK_CAMPAIGNS_SQL = """
        SELECT
            cac.connection_id,
            cac.external_campaign_id,
            cac.name AS campaign_name,
            mo.marketplace_sku
        FROM canonical_advertising_campaign cac
        JOIN marketplace_connection mc ON mc.id = cac.connection_id
        JOIN marketplace_offer mo ON mo.marketplace_connection_id = cac.connection_id
        JOIN canonical_stock_current csc ON csc.marketplace_offer_id = mo.id
        WHERE mc.workspace_id = :workspaceId
          AND mc.status = 'ACTIVE'
          AND cac.status = 'active'
          AND csc.available = 0
        """;

    private static final String SPENDING_SKUS_SQL = """
        SELECT DISTINCT
            connection_id,
            marketplace_sku
        FROM fact_advertising
        WHERE connection_id IN (:connectionIds)
          AND ad_date = today()
          AND spend > 0
        SETTINGS final = 1
        """;

    @Override
    public String ruleType() {
        return AlertRuleType.AD_NO_STOCK.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        var pgParams = new MapSqlParameterSource("workspaceId", rule.workspaceId());
        List<Map<String, Object>> zeroStockRows = pgJdbc.queryForList(ZERO_STOCK_CAMPAIGNS_SQL, pgParams);

        if (zeroStockRows.isEmpty()) {
            autoResolveAll(rule);
            return;
        }

        Set<Long> connectionIds = zeroStockRows.stream()
            .map(row -> ((Number) row.get("connection_id")).longValue())
            .collect(Collectors.toSet());

        Set<String> spendingKeys = findSpendingSkus(connectionIds);

        Map<Long, List<Map<String, Object>>> violationsByConnection = zeroStockRows.stream()
            .filter(row -> {
                long connId = ((Number) row.get("connection_id")).longValue();
                String sku = (String) row.get("marketplace_sku");
                return spendingKeys.contains(connId + ":" + sku);
            })
            .collect(Collectors.groupingBy(
                row -> ((Number) row.get("connection_id")).longValue()));

        for (var entry : violationsByConnection.entrySet()) {
            long connectionId = entry.getKey();
            List<Map<String, Object>> violations = entry.getValue();

            boolean alreadyOpen = !alertEventRepository
                .findActiveByRuleAndConnection(rule.id(), connectionId).isEmpty();
            if (!alreadyOpen) {
                String details = buildDetails(violations);
                alertEventService.createRuleBasedAlert(
                    rule.id(), rule.workspaceId(), connectionId,
                    ruleType(), rule.severity(),
                    MessageCodes.AD_NO_STOCK_TITLE,
                    details, rule.blocksAutomation());
            }
        }

        autoResolveCleared(rule, violationsByConnection.keySet());
    }

    private Set<String> findSpendingSkus(Set<Long> connectionIds) {
        if (connectionIds.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            var chParams = new MapSqlParameterSource("connectionIds", connectionIds);
            List<Map<String, Object>> rows = clickHouseJdbc.ch()
                .queryForList(SPENDING_SKUS_SQL, chParams);
            return rows.stream()
                .map(row -> ((Number) row.get("connection_id")).longValue()
                    + ":" + row.get("marketplace_sku"))
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("AdNoStockChecker: ClickHouse spend check skipped, alert accuracy degraded: error={}", e.getMessage());
            return Collections.emptySet();
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

    private String buildDetails(List<Map<String, Object>> violations) {
        try {
            List<Map<String, Object>> items = violations.stream()
                .map(row -> Map.<String, Object>of(
                    "campaign_name", row.get("campaign_name"),
                    "external_campaign_id", row.get("external_campaign_id"),
                    "marketplace_sku", row.get("marketplace_sku")))
                .toList();
            return objectMapper.writeValueAsString(Map.of("zero_stock_campaigns", items));
        } catch (Exception e) {
            return null;
        }
    }

}
