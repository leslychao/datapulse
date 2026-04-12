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
 * Fires when a policy keeps making BID_UP decisions but DRR / impressions
 * show no improvement over N consecutive runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutobidNoEffectChecker implements AlertChecker {

  private final NamedParameterJdbcTemplate pgJdbc;
  private final AlertEventService alertEventService;
  private final AlertEventRepository alertEventRepository;
  private final ObjectMapper objectMapper;

  private static final int DEFAULT_MIN_RUNS = 5;

  private static final String NO_EFFECT_SQL = """
      WITH recent_runs AS (
        SELECT br.id, br.bid_policy_id,
               ROW_NUMBER() OVER (
                 PARTITION BY br.bid_policy_id ORDER BY br.created_at DESC) AS rn
        FROM bidding_run br
        INNER JOIN bid_policy bp ON bp.id = br.bid_policy_id
        WHERE bp.workspace_id = :workspaceId
          AND bp.status = 'ACTIVE'
          AND br.status = 'COMPLETED'
      ),
      run_decisions AS (
        SELECT rr.bid_policy_id,
               COUNT(*) FILTER (WHERE bd.decision_type = 'BID_UP') AS bid_up_count,
               COUNT(*) FILTER (WHERE bd.decision_type = 'HOLD') AS hold_count,
               COUNT(*) AS total
        FROM recent_runs rr
        INNER JOIN bid_decision bd ON bd.bidding_run_id = rr.id
        WHERE rr.rn <= :minRuns
        GROUP BY rr.bid_policy_id
      )
      SELECT rd.bid_policy_id AS policy_id,
             bp.name AS policy_name,
             rd.bid_up_count,
             rd.total
      FROM run_decisions rd
      INNER JOIN bid_policy bp ON bp.id = rd.bid_policy_id
      WHERE rd.bid_up_count > rd.total * 0.7
      """;

  @Override
  public String ruleType() {
    return AlertRuleType.AUTOBID_NO_EFFECT.name();
  }

  @Override
  public void check(AlertRuleResponse rule) {
    Map<String, Object> config = parseConfig(rule.config());
    int minRuns = getInt(config, "min_runs", DEFAULT_MIN_RUNS);

    List<Map<String, Object>> rows = pgJdbc.queryForList(
        NO_EFFECT_SQL,
        new MapSqlParameterSource()
            .addValue("workspaceId", rule.workspaceId())
            .addValue("minRuns", minRuns));

    if (rows.isEmpty()) {
      autoResolveAll(rule);
      return;
    }

    boolean alreadyOpen = !alertEventRepository
        .findActiveByRule(rule.id()).isEmpty();

    if (!alreadyOpen) {
      String details = buildDetails(rows, minRuns);
      alertEventService.createRuleBasedAlert(
          rule.id(), rule.workspaceId(), null,
          ruleType(), rule.severity(),
          MessageCodes.AUTOBID_NO_EFFECT_TITLE,
          details, rule.blocksAutomation());
    }
  }

  private void autoResolveAll(AlertRuleResponse rule) {
    alertEventRepository.findActiveByRule(rule.id())
        .forEach(event -> alertEventService.autoResolve(
            rule.id(), event.connectionId(), rule.workspaceId()));
  }

  private String buildDetails(List<Map<String, Object>> rows, int minRuns) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "policies", rows.stream()
              .map(row -> Map.of(
                  "policy_id", row.get("policy_id"),
                  "policy_name", row.get("policy_name"),
                  "bid_up_count", row.get("bid_up_count"),
                  "total_decisions", row.get("total")))
              .toList(),
          "min_runs_checked", minRuns));
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
