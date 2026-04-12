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
 * Fires when a strategy hits its max bid constraint on most offers —
 * meaning it has no room to increase bids further.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutobidStrategyExhaustedChecker implements AlertChecker {

  private final NamedParameterJdbcTemplate pgJdbc;
  private final AlertEventService alertEventService;
  private final AlertEventRepository alertEventRepository;
  private final ObjectMapper objectMapper;

  private static final double DEFAULT_EXHAUSTION_THRESHOLD = 0.8;

  private static final String EXHAUSTED_SQL = """
      WITH latest_run AS (
        SELECT br.id AS run_id, br.bid_policy_id,
               ROW_NUMBER() OVER (
                 PARTITION BY br.bid_policy_id ORDER BY br.created_at DESC) AS rn
        FROM bidding_run br
        INNER JOIN bid_policy bp ON bp.id = br.bid_policy_id
        WHERE bp.workspace_id = :workspaceId
          AND bp.status = 'ACTIVE'
          AND br.status = 'COMPLETED'
      ),
      run_stats AS (
        SELECT lr.bid_policy_id,
               COUNT(*) AS total_decisions,
               COUNT(*) FILTER (
                 WHERE bd.decision_type = 'HOLD'
                   AND bd.explanation_summary LIKE '%%max bid%%'
               ) AS at_max_count
        FROM latest_run lr
        INNER JOIN bid_decision bd ON bd.bidding_run_id = lr.run_id
        WHERE lr.rn = 1
        GROUP BY lr.bid_policy_id
      )
      SELECT rs.bid_policy_id AS policy_id,
             bp.name AS policy_name,
             rs.at_max_count,
             rs.total_decisions
      FROM run_stats rs
      INNER JOIN bid_policy bp ON bp.id = rs.bid_policy_id
      WHERE rs.total_decisions > 0
        AND rs.at_max_count::double precision / rs.total_decisions >= :threshold
      """;

  @Override
  public String ruleType() {
    return AlertRuleType.AUTOBID_STRATEGY_EXHAUSTED.name();
  }

  @Override
  public void check(AlertRuleResponse rule) {
    Map<String, Object> config = parseConfig(rule.config());
    double threshold = getDouble(config,
        "exhaustion_threshold", DEFAULT_EXHAUSTION_THRESHOLD);

    List<Map<String, Object>> rows = pgJdbc.queryForList(
        EXHAUSTED_SQL,
        new MapSqlParameterSource()
            .addValue("workspaceId", rule.workspaceId())
            .addValue("threshold", threshold));

    if (rows.isEmpty()) {
      autoResolveAll(rule);
      return;
    }

    boolean alreadyOpen = !alertEventRepository
        .findActiveByRule(rule.id()).isEmpty();

    if (!alreadyOpen) {
      String details = buildDetails(rows, threshold);
      alertEventService.createRuleBasedAlert(
          rule.id(), rule.workspaceId(), null,
          ruleType(), rule.severity(),
          MessageCodes.AUTOBID_STRATEGY_EXHAUSTED_TITLE,
          details, rule.blocksAutomation());
    }
  }

  private void autoResolveAll(AlertRuleResponse rule) {
    alertEventRepository.findActiveByRule(rule.id())
        .forEach(event -> alertEventService.autoResolve(
            rule.id(), event.connectionId(), rule.workspaceId()));
  }

  private String buildDetails(List<Map<String, Object>> rows,
      double threshold) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "policies", rows.stream()
              .map(row -> Map.of(
                  "policy_id", row.get("policy_id"),
                  "policy_name", row.get("policy_name"),
                  "at_max_count", row.get("at_max_count"),
                  "total_decisions", row.get("total_decisions")))
              .toList(),
          "exhaustion_threshold_pct", (int) (threshold * 100)));
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

  private double getDouble(Map<String, Object> config, String key,
      double defaultValue) {
    Object value = config.get(key);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return defaultValue;
  }
}
