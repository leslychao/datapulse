package io.datapulse.audit.domain.checker;

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
public class MissingSyncChecker implements AlertChecker {

    private final NamedParameterJdbcTemplate jdbc;
    private final AlertEventService alertEventService;
    private final AlertEventRepository alertEventRepository;
    private final ObjectMapper objectMapper;

    private static final String LAST_SYNC_QUERY = """
            SELECT mc.id AS connection_id, je.data_domain,
                   MAX(je.started_at) AS last_started_at,
                   EXTRACT(EPOCH FROM (NOW() - MAX(je.started_at))) / 60 AS minutes_since_sync
            FROM marketplace_connection mc
            LEFT JOIN job_execution je ON je.connection_id = mc.id AND je.status = 'COMPLETED'
            WHERE mc.workspace_id = :workspaceId AND mc.status = 'ACTIVE'
            GROUP BY mc.id, je.data_domain
            """;

    @Override
    public String ruleType() {
        return AlertRuleType.MISSING_SYNC.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        Map<String, Object> config = parseConfig(rule.config());
        double expectedIntervalMinutes = getDouble(config, "expected_interval_minutes", 60.0);
        double toleranceFactor = getDouble(config, "tolerance_factor", 2.0);
        double thresholdMinutes = expectedIntervalMinutes * toleranceFactor;

        var params = new MapSqlParameterSource("workspaceId", rule.workspaceId());
        List<Map<String, Object>> rows = jdbc.queryForList(LAST_SYNC_QUERY, params);

        Map<Long, List<Map<String, Object>>> byConnection = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        row -> ((Number) row.get("connection_id")).longValue()));

        for (var entry : byConnection.entrySet()) {
            long connectionId = entry.getKey();
            List<Map<String, Object>> domainRows = entry.getValue();

            boolean missing = domainRows.stream().anyMatch(row -> {
                Object minutes = row.get("minutes_since_sync");
                if (minutes == null) {
                    return true;
                }
                return ((Number) minutes).doubleValue() > thresholdMinutes;
            });

            if (missing) {
                boolean alreadyOpen = !alertEventRepository
                        .findActiveByRuleAndConnection(rule.id(), connectionId).isEmpty();
                if (!alreadyOpen) {
                    alertEventService.createRuleBasedAlert(
                            rule.id(), rule.workspaceId(), connectionId,
                            ruleType(), rule.severity(),
                            "Missing sync detected for connection %d".formatted(connectionId),
                            buildDetails(domainRows, thresholdMinutes), rule.blocksAutomation());
                }
            } else {
                alertEventService.autoResolve(rule.id(), connectionId, rule.workspaceId());
            }
        }
    }

    private String buildDetails(List<Map<String, Object>> domainRows, double thresholdMinutes) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "threshold_minutes", thresholdMinutes,
                    "domains", domainRows.stream()
                            .map(row -> Map.of(
                                    "domain", String.valueOf(row.get("data_domain")),
                                    "minutes_since_sync", row.get("minutes_since_sync")))
                            .toList()));
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
