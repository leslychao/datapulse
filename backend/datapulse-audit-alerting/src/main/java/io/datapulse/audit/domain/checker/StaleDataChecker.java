package io.datapulse.audit.domain.checker;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class StaleDataChecker implements AlertChecker {

    private final NamedParameterJdbcTemplate jdbc;
    private final AlertEventService alertEventService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    private static final Set<String> FINANCE_DOMAINS = Set.of("SALES", "RETURNS", "FINANCE");

    private static final String SYNC_STATE_QUERY = """
            SELECT mc.id AS connection_id, mss.data_domain,
                   EXTRACT(EPOCH FROM (NOW() - mss.last_success_at)) / 3600 AS hours_since_sync
            FROM marketplace_connection mc
            JOIN marketplace_sync_state mss ON mss.connection_id = mc.id
            WHERE mc.workspace_id = :workspaceId AND mc.status = 'ACTIVE'
            """;

    @Override
    public String ruleType() {
        return AlertRuleType.STALE_DATA.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        Map<String, Object> config = parseConfig(rule.config());
        double financeStaleHours = getDouble(config, "finance_stale_hours", 24.0);
        double stateStaleHours = getDouble(config, "state_stale_hours", 48.0);

        var params = new MapSqlParameterSource("workspaceId", rule.workspaceId());
        List<Map<String, Object>> rows = jdbc.queryForList(SYNC_STATE_QUERY, params);

        Map<Long, List<Map<String, Object>>> byConnection = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        row -> ((Number) row.get("connection_id")).longValue()));

        for (var entry : byConnection.entrySet()) {
            long connectionId = entry.getKey();
            List<Map<String, Object>> domainRows = entry.getValue();

            boolean stale = domainRows.stream().anyMatch(row -> {
                String domain = (String) row.get("data_domain");
                double hours = ((Number) row.get("hours_since_sync")).doubleValue();
                double threshold = FINANCE_DOMAINS.contains(domain) ? financeStaleHours : stateStaleHours;
                return hours > threshold;
            });

            if (stale) {
                boolean alreadyOpen = !alertEventRepository
                        .findActiveByRuleAndConnection(rule.id(), connectionId).isEmpty();
                if (!alreadyOpen) {
                    String details = buildDetails(domainRows, financeStaleHours, stateStaleHours);
                    alertEventService.createRuleBasedAlert(
                            rule.id(), rule.workspaceId(), connectionId,
                            ruleType(), rule.severity(),
                            "Stale data detected for connection %d".formatted(connectionId),
                            details, rule.blocksAutomation());
                }
            } else {
                alertEventService.autoResolve(rule.id(), connectionId, rule.workspaceId());
            }
        }
    }

    private String buildDetails(List<Map<String, Object>> domainRows,
                                double financeThreshold, double stateThreshold) {
        try {
            List<Map<String, Object>> staleDetails = domainRows.stream()
                    .map(row -> Map.<String, Object>of(
                            "domain", row.get("data_domain"),
                            "hours_since_sync", row.get("hours_since_sync"),
                            "threshold", FINANCE_DOMAINS.contains(row.get("data_domain"))
                                    ? financeThreshold : stateThreshold))
                    .toList();
            return objectMapper.writeValueAsString(Map.of("stale_domains", staleDetails));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse alert rule config, using defaults: error={}", e.getMessage());
            return Map.of();
        }
    }

    private double getDouble(Map<String, Object> config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }
}
