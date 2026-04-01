package io.datapulse.audit.domain.checker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.domain.AlertEventService;
import io.datapulse.audit.domain.AlertRuleType;
import io.datapulse.audit.persistence.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MismatchChecker implements AlertChecker {

    private final NamedParameterJdbcTemplate jdbc;
    private final AlertEventService alertEventService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    private static final String PRICE_COVERAGE_QUERY = """
            SELECT mc.id AS connection_id, COUNT(*) AS orphan_count
            FROM marketplace_offer mo
            JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
            WHERE mo.status = 'ACTIVE'
              AND mc.workspace_id = :workspaceId
              AND NOT EXISTS (
                  SELECT 1 FROM canonical_price_current cpc WHERE cpc.marketplace_offer_id = mo.id
              )
            GROUP BY mc.id
            """;

    private static final String ORDER_ORPHANS_QUERY = """
            SELECT mc.id AS connection_id, COUNT(*) AS orphan_count
            FROM canonical_order co
            JOIN marketplace_connection mc ON mc.id = co.connection_id
            WHERE mc.workspace_id = :workspaceId
              AND NOT EXISTS (
                  SELECT 1 FROM marketplace_offer mo WHERE mo.id = co.offer_id
              )
            GROUP BY mc.id
            """;

    private static final String FINANCE_ORPHANS_QUERY = """
            SELECT mc.id AS connection_id, COUNT(*) AS orphan_count
            FROM canonical_finance_entry cfe
            JOIN marketplace_connection mc ON mc.id = cfe.connection_id
            WHERE mc.workspace_id = :workspaceId
              AND NOT EXISTS (
                  SELECT 1 FROM canonical_sale cs WHERE cs.id = cfe.sale_id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM canonical_order co WHERE co.id = cfe.order_id
              )
            GROUP BY mc.id
            """;

    @Override
    public String ruleType() {
        return AlertRuleType.MISMATCH.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        Map<String, Object> config = parseConfig(rule.config());
        int maxOrphanCount = getInt(config, "max_orphan_count", 5);

        Map<Long, List<String>> violations = new HashMap<>();

        runCheck(rule.workspaceId(), PRICE_COVERAGE_QUERY, maxOrphanCount, "price_coverage", violations);
        runCheck(rule.workspaceId(), ORDER_ORPHANS_QUERY, maxOrphanCount, "order_orphans", violations);
        runCheck(rule.workspaceId(), FINANCE_ORPHANS_QUERY, maxOrphanCount, "finance_orphans", violations);

        for (var entry : violations.entrySet()) {
            long connectionId = entry.getKey();
            List<String> failedChecks = entry.getValue();

            boolean alreadyOpen = !alertEventRepository
                    .findActiveByRuleAndConnection(rule.id(), connectionId).isEmpty();
            if (!alreadyOpen) {
                String details = buildDetails(failedChecks);
                alertEventService.createRuleBasedAlert(
                        rule.id(), rule.workspaceId(), connectionId,
                        ruleType(), rule.severity(),
                        "Data mismatch detected for connection %d".formatted(connectionId),
                        details, rule.blocksAutomation());
            }
        }

        autoResolveCleared(rule, violations);
    }

    private void runCheck(long workspaceId, String sql, int threshold,
                          String checkName, Map<Long, List<String>> violations) {
        try {
            var params = new MapSqlParameterSource("workspaceId", workspaceId);
            List<Map<String, Object>> rows = jdbc.queryForList(sql, params);

            for (Map<String, Object> row : rows) {
                long connectionId = ((Number) row.get("connection_id")).longValue();
                long orphanCount = ((Number) row.get("orphan_count")).longValue();
                if (orphanCount > threshold) {
                    violations.computeIfAbsent(connectionId, k -> new ArrayList<>()).add(checkName);
                }
            }
        } catch (Exception e) {
            log.debug("Mismatch check '{}' skipped (table may not exist yet): error={}",
                    checkName, e.getMessage());
        }
    }

    private void autoResolveCleared(AlertRuleResponse rule, Map<Long, List<String>> currentViolations) {
        List<Long> connectionIds = alertEventRepository
                .findActiveByRule(rule.id())
                .stream()
                .filter(event -> event.connectionId() != null)
                .map(event -> event.connectionId())
                .distinct()
                .toList();

        for (long connectionId : connectionIds) {
            if (!currentViolations.containsKey(connectionId)) {
                alertEventService.autoResolve(rule.id(), connectionId, rule.workspaceId());
            }
        }
    }

    private String buildDetails(List<String> failedChecks) {
        try {
            return objectMapper.writeValueAsString(Map.of("failed_checks", failedChecks));
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

    private int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
