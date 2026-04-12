package io.datapulse.audit.domain.checker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

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
 * Fires an alert when today's total ad spend for autobidding-managed products
 * exceeds 2× the 7-day daily average.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutobidSpendSpikeChecker implements AlertChecker {

  private final AlertClickHouseJdbc clickHouseJdbc;
  private final NamedParameterJdbcTemplate pgJdbc;
  private final AlertEventService alertEventService;
  private final AlertEventRepository alertEventRepository;
  private final ObjectMapper objectMapper;

  private static final BigDecimal DEFAULT_SPIKE_MULTIPLIER = BigDecimal.valueOf(2);
  private static final int DEFAULT_LOOKBACK_DAYS = 7;

  private static final String CONNECTION_IDS_SQL = """
      SELECT id FROM marketplace_connection
      WHERE workspace_id = :workspaceId AND status = 'ACTIVE'
      """;

  private static final String ASSIGNED_SKUS_SQL = """
      SELECT mo.marketplace_sku
      FROM bid_policy_assignment bpa
      INNER JOIN bid_policy bp ON bp.id = bpa.bid_policy_id
      INNER JOIN marketplace_offer mo ON mo.id = bpa.marketplace_offer_id
      WHERE bp.workspace_id = :workspaceId
        AND bp.status = 'ACTIVE'
      """;

  private static final String SPEND_SQL = """
      SELECT
          sum(if(fa.ad_date = today(), fa.spend, toDecimal64(0, 2))) AS today_spend,
          sum(if(fa.ad_date < today(), fa.spend, toDecimal64(0, 2))) /
              greatest(dateDiff('day', today() - :lookbackDays, today()), 1) AS avg_daily_spend
      FROM fact_advertising AS fa
      WHERE fa.ad_date >= today() - :lookbackDays
        AND fa.connection_id IN (:connectionIds)
        AND fa.marketplace_sku IN (:assignedSkus)
      SETTINGS final = 1
      """;

  @Override
  public String ruleType() {
    return AlertRuleType.AUTOBID_SPEND_SPIKE.name();
  }

  @Override
  public void check(AlertRuleResponse rule) {
    Map<String, Object> config = parseConfig(rule.config());
    BigDecimal spikeMultiplier = getBigDecimal(config, "spike_multiplier", DEFAULT_SPIKE_MULTIPLIER);
    int lookbackDays = getInt(config, "lookback_days", DEFAULT_LOOKBACK_DAYS);

    List<Long> connectionIds = findActiveConnectionIds(rule.workspaceId());
    if (connectionIds.isEmpty()) {
      return;
    }

    List<String> assignedSkus = pgJdbc.queryForList(
        ASSIGNED_SKUS_SQL,
        new MapSqlParameterSource("workspaceId", rule.workspaceId()),
        String.class);

    if (assignedSkus.isEmpty()) {
      autoResolveAll(rule);
      return;
    }

    Map<String, Object> spendRow;
    try {
      spendRow = clickHouseJdbc.ch().queryForMap(SPEND_SQL,
          new MapSqlParameterSource()
              .addValue("connectionIds", connectionIds)
              .addValue("assignedSkus", assignedSkus)
              .addValue("lookbackDays", lookbackDays));
    } catch (Exception e) {
      log.debug("AutobidSpendSpikeChecker skipped (ClickHouse unavailable): error={}",
          e.getMessage());
      return;
    }

    BigDecimal todaySpend = toBigDecimal(spendRow.get("today_spend"));
    BigDecimal avgDailySpend = toBigDecimal(spendRow.get("avg_daily_spend"));

    if (todaySpend == null || avgDailySpend == null
        || avgDailySpend.compareTo(BigDecimal.ZERO) == 0) {
      autoResolveAll(rule);
      return;
    }

    BigDecimal threshold = avgDailySpend.multiply(spikeMultiplier);

    if (todaySpend.compareTo(threshold) > 0) {
      boolean alreadyOpen = !alertEventRepository
          .findActiveByRule(rule.id()).isEmpty();

      if (!alreadyOpen) {
        String details = buildDetails(todaySpend, avgDailySpend, spikeMultiplier, lookbackDays);
        alertEventService.createRuleBasedAlert(
            rule.id(), rule.workspaceId(), null,
            ruleType(), rule.severity(),
            MessageCodes.AUTOBID_SPEND_SPIKE_TITLE,
            details, rule.blocksAutomation());
      }
    } else {
      autoResolveAll(rule);
    }
  }

  private void autoResolveAll(AlertRuleResponse rule) {
    alertEventRepository.findActiveByRule(rule.id())
        .forEach(event -> alertEventService.autoResolve(
            rule.id(), event.connectionId(), rule.workspaceId()));
  }

  private List<Long> findActiveConnectionIds(long workspaceId) {
    return pgJdbc.queryForList(CONNECTION_IDS_SQL,
        new MapSqlParameterSource("workspaceId", workspaceId), Long.class);
  }

  private String buildDetails(BigDecimal todaySpend, BigDecimal avgDailySpend,
                              BigDecimal multiplier, int lookbackDays) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "today_spend", todaySpend.setScale(2, RoundingMode.HALF_UP),
          "avg_daily_spend", avgDailySpend.setScale(2, RoundingMode.HALF_UP),
          "spike_multiplier", multiplier,
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
