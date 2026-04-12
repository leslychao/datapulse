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
 * Fires an alert when > 20% of autobidding-managed products have
 * DRR exceeding 1.5 × their policy's targetDrr over the last 7 days.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutobidHighDrrClusterChecker implements AlertChecker {

  private final AlertClickHouseJdbc clickHouseJdbc;
  private final NamedParameterJdbcTemplate pgJdbc;
  private final AlertEventService alertEventService;
  private final AlertEventRepository alertEventRepository;
  private final ObjectMapper objectMapper;

  private static final BigDecimal DEFAULT_DRR_MULTIPLIER = new BigDecimal("1.5");
  private static final BigDecimal DEFAULT_CLUSTER_THRESHOLD_PCT = new BigDecimal("20");
  private static final int DEFAULT_LOOKBACK_DAYS = 7;

  private static final String ASSIGNED_OFFERS_SQL = """
      SELECT bpa.marketplace_offer_id
      FROM bid_policy_assignment bpa
      INNER JOIN bid_policy bp ON bp.id = bpa.bid_policy_id
      WHERE bp.workspace_id = :workspaceId
        AND bp.status = 'ACTIVE'
      """;

  private static final String DRR_BY_OFFER_SQL = """
      SELECT
          fa.connection_id,
          fa.marketplace_sku,
          if(ff_rev.total_revenue > 0,
             sum(fa.spend) / ff_rev.total_revenue * 100,
             toDecimal64(0, 2)) AS drr_pct
      FROM fact_advertising AS fa
      LEFT JOIN (
          SELECT dp.marketplace_sku, sum(ff.revenue_amount) AS total_revenue
          FROM fact_finance AS ff
          INNER JOIN dim_product AS dp ON ff.seller_sku_id = dp.seller_sku_id
          WHERE ff.finance_date >= today() - :lookbackDays
            AND ff.attribution_level IN ('POSTING', 'PRODUCT')
            AND ff.connection_id IN (:connectionIds)
          GROUP BY dp.marketplace_sku
      ) AS ff_rev ON fa.marketplace_sku = ff_rev.marketplace_sku
      WHERE fa.ad_date >= today() - :lookbackDays
        AND fa.connection_id IN (:connectionIds)
      GROUP BY fa.connection_id, fa.marketplace_sku, ff_rev.total_revenue
      HAVING sum(fa.spend) > 0
      SETTINGS final = 1
      """;

  private static final String CONNECTION_IDS_SQL = """
      SELECT id FROM marketplace_connection
      WHERE workspace_id = :workspaceId AND status = 'ACTIVE'
      """;

  @Override
  public String ruleType() {
    return AlertRuleType.AUTOBID_HIGH_DRR_CLUSTER.name();
  }

  @Override
  public void check(AlertRuleResponse rule) {
    Map<String, Object> config = parseConfig(rule.config());
    BigDecimal drrMultiplier = getBigDecimal(config, "drr_multiplier", DEFAULT_DRR_MULTIPLIER);
    BigDecimal clusterThresholdPct = getBigDecimal(config, "cluster_threshold_pct", DEFAULT_CLUSTER_THRESHOLD_PCT);
    BigDecimal targetDrr = getBigDecimal(config, "target_drr_pct", null);
    int lookbackDays = getInt(config, "lookback_days", DEFAULT_LOOKBACK_DAYS);

    if (targetDrr == null) {
      log.debug("AutobidHighDrrClusterChecker: no target_drr_pct configured, skipping");
      return;
    }

    List<Long> connectionIds = findActiveConnectionIds(rule.workspaceId());
    if (connectionIds.isEmpty()) {
      return;
    }

    List<Long> assignedOfferIds = pgJdbc.queryForList(
        ASSIGNED_OFFERS_SQL,
        new MapSqlParameterSource("workspaceId", rule.workspaceId()),
        Long.class);

    if (assignedOfferIds.isEmpty()) {
      autoResolveAll(rule);
      return;
    }

    BigDecimal drrCeiling = targetDrr.multiply(drrMultiplier);

    List<Map<String, Object>> rows;
    try {
      rows = clickHouseJdbc.ch().queryForList(DRR_BY_OFFER_SQL,
          new MapSqlParameterSource()
              .addValue("connectionIds", connectionIds)
              .addValue("lookbackDays", lookbackDays));
    } catch (Exception e) {
      log.debug("AutobidHighDrrClusterChecker skipped (ClickHouse unavailable): error={}",
          e.getMessage());
      return;
    }

    long violatingCount = rows.stream()
        .filter(row -> {
          BigDecimal drr = toBigDecimal(row.get("drr_pct"));
          return drr != null && drr.compareTo(drrCeiling) > 0;
        })
        .count();

    BigDecimal violatingPct = BigDecimal.valueOf(violatingCount * 100)
        .divide(BigDecimal.valueOf(assignedOfferIds.size()), 2, java.math.RoundingMode.HALF_UP);

    if (violatingPct.compareTo(clusterThresholdPct) > 0) {
      boolean alreadyOpen = !alertEventRepository
          .findActiveByRule(rule.id()).isEmpty();

      if (!alreadyOpen) {
        String details = buildDetails(violatingCount, assignedOfferIds.size(),
            violatingPct, drrCeiling, lookbackDays);
        alertEventService.createRuleBasedAlert(
            rule.id(), rule.workspaceId(), null,
            ruleType(), rule.severity(),
            MessageCodes.AUTOBID_HIGH_DRR_CLUSTER_TITLE,
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

  private String buildDetails(long violatingCount, int totalAssigned,
                              BigDecimal violatingPct, BigDecimal drrCeiling, int lookbackDays) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "violating_count", violatingCount,
          "total_assigned", totalAssigned,
          "violating_pct", violatingPct,
          "drr_ceiling", drrCeiling,
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
