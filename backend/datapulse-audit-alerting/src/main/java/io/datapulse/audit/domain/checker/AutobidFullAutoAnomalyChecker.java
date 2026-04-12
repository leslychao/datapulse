package io.datapulse.audit.domain.checker;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.audit.api.AlertRuleResponse;
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
 * Fires an alert when FULL_AUTO policies have recent paused (blast-radius
 * breached) or failed runs, indicating the autobidder may be acting
 * unsafely without human oversight.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutobidFullAutoAnomalyChecker implements AlertChecker {

  private final NamedParameterJdbcTemplate pgJdbc;
  private final AlertEventService alertEventService;
  private final AlertEventRepository alertEventRepository;
  private final ObjectMapper objectMapper;

  private static final int DEFAULT_LOOKBACK_DAYS = 3;

  private static final String ANOMALY_SQL = """
      SELECT bp.id AS policy_id,
             bp.name AS policy_name,
             COUNT(*) FILTER (WHERE br.status = 'PAUSED') AS paused_runs,
             COUNT(*) FILTER (WHERE br.status = 'FAILED') AS failed_runs
      FROM bid_policy bp
      INNER JOIN bidding_run br ON br.bid_policy_id = bp.id
      WHERE bp.workspace_id = :workspaceId
        AND bp.execution_mode = 'FULL_AUTO'
        AND bp.status = 'ACTIVE'
        AND br.created_at >= now() - make_interval(days => :lookbackDays)
      GROUP BY bp.id, bp.name
      HAVING COUNT(*) FILTER (WHERE br.status IN ('PAUSED', 'FAILED')) > 0
      """;

  @Override
  public String ruleType() {
    return AlertRuleType.AUTOBID_FULL_AUTO_ANOMALY.name();
  }

  @Override
  public void check(AlertRuleResponse rule) {
    Map<String, Object> config = parseConfig(rule.config());
    int lookbackDays = getInt(config, "lookback_days", DEFAULT_LOOKBACK_DAYS);

    List<Map<String, Object>> rows = pgJdbc.queryForList(
        ANOMALY_SQL,
        new MapSqlParameterSource()
            .addValue("workspaceId", rule.workspaceId())
            .addValue("lookbackDays", lookbackDays));

    if (rows.isEmpty()) {
      autoResolveAll(rule);
      return;
    }

    boolean alreadyOpen = !alertEventRepository
        .findActiveByRule(rule.id()).isEmpty();

    if (!alreadyOpen) {
      String details = buildDetails(rows, lookbackDays);
      alertEventService.createRuleBasedAlert(
          rule.id(), rule.workspaceId(), null,
          ruleType(), rule.severity(),
          MessageCodes.AUTOBID_FULL_AUTO_ANOMALY_TITLE,
          details, rule.blocksAutomation());
    }
  }

  private void autoResolveAll(AlertRuleResponse rule) {
    alertEventRepository.findActiveByRule(rule.id())
        .forEach(event -> alertEventService.autoResolve(
            rule.id(), event.connectionId(), rule.workspaceId()));
  }

  private String buildDetails(List<Map<String, Object>> rows,
      int lookbackDays) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "policies", rows.stream()
              .map(row -> Map.of(
                  "policy_id", row.get("policy_id"),
                  "policy_name", row.get("policy_name"),
                  "paused_runs", row.get("paused_runs"),
                  "failed_runs", row.get("failed_runs")))
              .toList(),
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
        log.warn("Failed to parse alert rule config: error={}", e.getMessage());
      }
    }
    return Map.of();
  }

  private int getInt(Map<String, Object> config, String key,
      int defaultValue) {
    Object value = config.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    return defaultValue;
  }
}
