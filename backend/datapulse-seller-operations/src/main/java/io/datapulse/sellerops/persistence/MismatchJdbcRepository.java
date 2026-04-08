package io.datapulse.sellerops.persistence;

import io.datapulse.sellerops.api.MismatchFilter;
import io.datapulse.sellerops.api.MismatchSummaryResponse.TimelinePoint;
import io.datapulse.sellerops.api.MismatchSummaryResponse.TypeDistribution;
import io.datapulse.sellerops.domain.MismatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MismatchJdbcRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String BASE_SELECT = """
      SELECT
          ae.id                                      AS mismatch_id,
          ae.details->>'mismatch_type'               AS mismatch_type,
          (ae.details->>'offer_id')::bigint           AS offer_id,
          ae.details->>'offer_name'                   AS offer_name,
          ae.details->>'sku_code'                     AS sku_code,
          ae.details->>'expected_value'               AS expected_value,
          ae.details->>'actual_value'                 AS actual_value,
          (ae.details->>'delta_pct')::numeric         AS delta_pct,
          ae.severity,
          ae.status,
          ae.resolved_reason,
          ae.opened_at                                AS detected_at,
          ae.resolved_at,
          mc.name                                     AS connection_name,
          mc.marketplace_type                         AS marketplace_type,
          pa_latest.id                                AS related_action_id
      """;

  private static final String FROM_JOINS = """
      FROM alert_event ae
      LEFT JOIN marketplace_connection mc ON mc.id = ae.connection_id
      LEFT JOIN LATERAL (
          SELECT pa2.id
          FROM price_action pa2
          WHERE pa2.marketplace_offer_id = (ae.details->>'offer_id')::bigint
          ORDER BY pa2.created_at DESC
          LIMIT 1
      ) pa_latest ON ae.details->>'mismatch_type' = 'PRICE'
      """;

  private static final String MISMATCH_CONDITION =
      " ae.details->>'mismatch_type' IS NOT NULL";

  private static final Map<String, String> SORT_COLUMNS = Map.of(
      "detectedAt", "ae.opened_at",
      "severity", "ae.severity",
      "status", "ae.status",
      "type", "ae.details->>'mismatch_type'",
      "deltaPct", "(ae.details->>'delta_pct')::numeric",
      "offerName", "ae.details->>'offer_name'",
      "expectedValue", "ae.details->>'expected_value'",
      "actualValue", "ae.details->>'actual_value'",
      "resolution", "ae.resolved_reason"
  );

  public Page<MismatchRow> findAll(long workspaceId, MismatchFilter filter,
                                   Pageable pageable) {
    var where = new StringBuilder(" WHERE ae.workspace_id = :workspaceId AND ")
        .append(MISMATCH_CONDITION);
    var params = new MapSqlParameterSource("workspaceId", workspaceId);

    appendFilters(filter, where, params);

    String countSql = "SELECT COUNT(*) FROM alert_event ae WHERE ae.workspace_id = :workspaceId AND "
        + MISMATCH_CONDITION + where.toString().substring(
        where.indexOf("AND " + MISMATCH_CONDITION) + ("AND " + MISMATCH_CONDITION).length());

    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) " + FROM_JOINS + where, params, Long.class);
    if (total == null || total == 0) {
      return Page.empty(pageable);
    }

    String orderBy = buildOrderByClause(pageable.getSort());
    String querySql = BASE_SELECT + FROM_JOINS + where
        + orderBy + " LIMIT :limit OFFSET :offset";
    params.addValue("limit", pageable.getPageSize());
    params.addValue("offset", pageable.getOffset());

    List<MismatchRow> content = jdbc.query(querySql, params, this::mapRow);
    return new PageImpl<>(content, pageable, total);
  }

  public Optional<MismatchRow> findById(long id, long workspaceId) {
    String sql = BASE_SELECT + FROM_JOINS
        + " WHERE ae.id = :id AND ae.workspace_id = :workspaceId AND "
        + MISMATCH_CONDITION;
    var params = new MapSqlParameterSource()
        .addValue("id", id)
        .addValue("workspaceId", workspaceId);

    List<MismatchRow> rows = jdbc.query(sql, params, this::mapRow);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public int acknowledge(long id, long workspaceId, long userId) {
    String sql = """
        UPDATE alert_event
        SET status = 'ACKNOWLEDGED',
            acknowledged_at = NOW(),
            acknowledged_by = :userId
        WHERE id = :id
          AND workspace_id = :workspaceId
          AND status = 'OPEN'
        """;
    var params = new MapSqlParameterSource()
        .addValue("id", id)
        .addValue("workspaceId", workspaceId)
        .addValue("userId", userId);
    return jdbc.update(sql, params);
  }

  public int bulkAcknowledge(long workspaceId, List<Long> ids, long userId) {
    if (CollectionUtils.isEmpty(ids)) {
      return 0;
    }
    String sql = """
        UPDATE alert_event
        SET status = 'ACKNOWLEDGED',
            acknowledged_at = NOW(),
            acknowledged_by = :userId
        WHERE workspace_id = :workspaceId
          AND id IN (:ids)
          AND status = 'OPEN'
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("ids", ids)
        .addValue("userId", userId);
    return jdbc.update(sql, params);
  }

  public int resolve(long id, long workspaceId, String resolution, String note,
                     long userId) {
    String sql = """
        UPDATE alert_event
        SET status = 'RESOLVED',
            resolved_at = NOW(),
            resolved_reason = :resolvedReason,
            resolved_by = :userId
        WHERE id = :id
          AND workspace_id = :workspaceId
          AND status IN ('OPEN', 'ACKNOWLEDGED')
        """;
    var params = new MapSqlParameterSource()
        .addValue("id", id)
        .addValue("workspaceId", workspaceId)
        .addValue("resolvedReason", buildResolvedReason(resolution, note))
        .addValue("userId", userId);
    return jdbc.update(sql, params);
  }

  public int bulkIgnore(long workspaceId, List<Long> ids, String reason, long userId) {
    String sql = """
        UPDATE alert_event
        SET status = 'RESOLVED',
            resolved_at = NOW(),
            resolved_reason = :resolvedReason,
            resolved_by = :userId
        WHERE workspace_id = :workspaceId
          AND id IN (:ids)
          AND status IN ('OPEN', 'ACKNOWLEDGED')
          AND details->>'mismatch_type' IS NOT NULL
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("ids", ids)
        .addValue("resolvedReason", buildResolvedReason("IGNORED", reason))
        .addValue("userId", userId);
    return jdbc.update(sql, params);
  }

  public SummaryData getSummaryData(long workspaceId) {
    String sql = """
        SELECT
            COUNT(*) FILTER (WHERE ae.status IN ('OPEN', 'ACKNOWLEDGED'))
                AS active_count,
            COUNT(*) FILTER (WHERE ae.status IN ('OPEN', 'ACKNOWLEDGED')
                AND ae.opened_at >= NOW() - INTERVAL '7 days')
                AS active_last_7d,
            COUNT(*) FILTER (WHERE ae.status IN ('OPEN', 'ACKNOWLEDGED')
                AND ae.opened_at >= NOW() - INTERVAL '14 days'
                AND ae.opened_at < NOW() - INTERVAL '7 days')
                AS active_prev_7d,
            COUNT(*) FILTER (WHERE ae.status IN ('OPEN', 'ACKNOWLEDGED')
                AND ae.severity = 'CRITICAL')
                AS critical_count,
            COUNT(*) FILTER (WHERE ae.status IN ('OPEN', 'ACKNOWLEDGED')
                AND ae.severity = 'CRITICAL'
                AND ae.opened_at >= NOW() - INTERVAL '7 days')
                AS critical_last_7d,
            COUNT(*) FILTER (WHERE ae.status IN ('OPEN', 'ACKNOWLEDGED')
                AND ae.severity = 'CRITICAL'
                AND ae.opened_at >= NOW() - INTERVAL '14 days'
                AND ae.opened_at < NOW() - INTERVAL '7 days')
                AS critical_prev_7d,
            AVG(EXTRACT(EPOCH FROM (NOW() - ae.opened_at)) / 3600)
                FILTER (WHERE ae.status IN ('OPEN', 'ACKNOWLEDGED'))
                AS avg_hours_unresolved,
            COUNT(*) FILTER (WHERE ae.status = 'AUTO_RESOLVED'
                AND ae.resolved_at::date = CURRENT_DATE)
                AS auto_resolved_today,
            COUNT(*) FILTER (WHERE ae.status = 'AUTO_RESOLVED'
                AND ae.resolved_at::date = CURRENT_DATE - 1)
                AS auto_resolved_yesterday
        FROM alert_event ae
        WHERE ae.workspace_id = :workspaceId
          AND ae.details->>'mismatch_type' IS NOT NULL
        """;
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.queryForObject(sql, params, (rs, rowNum) -> new SummaryData(
        rs.getLong("active_count"),
        rs.getLong("active_last_7d"),
        rs.getLong("active_prev_7d"),
        rs.getLong("critical_count"),
        rs.getLong("critical_last_7d"),
        rs.getLong("critical_prev_7d"),
        rs.getBigDecimal("avg_hours_unresolved"),
        rs.getLong("auto_resolved_today"),
        rs.getLong("auto_resolved_yesterday")
    ));
  }

  public List<TypeDistribution> getDistributionByType(long workspaceId) {
    String sql = """
        SELECT ae.details->>'mismatch_type' AS mismatch_type,
               COUNT(*)                      AS cnt
        FROM alert_event ae
        WHERE ae.workspace_id = :workspaceId
          AND ae.details->>'mismatch_type' IS NOT NULL
          AND ae.status IN ('OPEN', 'ACKNOWLEDGED')
        GROUP BY ae.details->>'mismatch_type'
        ORDER BY cnt DESC
        """;
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.query(sql, params, (rs, rowNum) ->
        new TypeDistribution(rs.getString("mismatch_type"), rs.getLong("cnt")));
  }

  public List<TimelinePoint> getTimeline(long workspaceId, int days) {
    String sql = """
        SELECT d.day::text AS day,
               COALESCE(n.cnt, 0) AS new_count,
               COALESCE(r.cnt, 0) AS resolved_count
        FROM generate_series(
                 CURRENT_DATE - :days,
                 CURRENT_DATE,
                 '1 day'::interval
             ) AS d(day)
        LEFT JOIN (
            SELECT ae.opened_at::date AS day, COUNT(*) AS cnt
            FROM alert_event ae
            WHERE ae.workspace_id = :workspaceId
              AND ae.details->>'mismatch_type' IS NOT NULL
              AND ae.opened_at >= CURRENT_DATE - :days
            GROUP BY ae.opened_at::date
        ) n ON n.day = d.day
        LEFT JOIN (
            SELECT ae.resolved_at::date AS day, COUNT(*) AS cnt
            FROM alert_event ae
            WHERE ae.workspace_id = :workspaceId
              AND ae.details->>'mismatch_type' IS NOT NULL
              AND ae.status IN ('RESOLVED', 'AUTO_RESOLVED')
              AND ae.resolved_at >= CURRENT_DATE - :days
            GROUP BY ae.resolved_at::date
        ) r ON r.day = d.day
        ORDER BY d.day
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("days", days);
    return jdbc.query(sql, params, (rs, rowNum) ->
        new TimelinePoint(
            rs.getString("day").substring(0, 10),
            rs.getLong("new_count"),
            rs.getLong("resolved_count")));
  }

  public Optional<MismatchDetailRow> findDetailById(long id, long workspaceId) {
    String sql = """
        SELECT
            ae.id                                      AS mismatch_id,
            ae.details->>'mismatch_type'               AS mismatch_type,
            (ae.details->>'offer_id')::bigint           AS offer_id,
            ae.details->>'offer_name'                   AS offer_name,
            ae.details->>'sku_code'                     AS sku_code,
            ae.details->>'expected_value'               AS expected_value,
            ae.details->>'actual_value'                 AS actual_value,
            (ae.details->>'delta_pct')::numeric         AS delta_pct,
            ae.severity,
            ae.status,
            ae.resolved_reason,
            ae.opened_at                                AS detected_at,
            mc.name                                     AS connection_name,
            mc.marketplace_type                         AS marketplace_type,
            mc.id                                       AS connection_id,
            ae.acknowledged_at,
            ack_user.name                               AS acknowledged_by_name,
            ae.resolved_at,
            res_user.name                               AS resolved_by_name,
            pa.id                                       AS related_action_id,
            pa.status                                   AS related_action_status,
            pa.target_price                             AS related_action_target_price,
            pa.updated_at                               AS related_action_executed_at,
            pa.reconciliation_source                    AS related_action_reconciliation_source
        FROM alert_event ae
        LEFT JOIN marketplace_connection mc ON mc.id = ae.connection_id
        LEFT JOIN app_user ack_user ON ack_user.id = ae.acknowledged_by
        LEFT JOIN app_user res_user ON res_user.id = ae.resolved_by
        LEFT JOIN LATERAL (
            SELECT pa2.id, pa2.status, pa2.target_price, pa2.updated_at,
                   pa2.reconciliation_source
            FROM price_action pa2
            WHERE pa2.marketplace_offer_id = (ae.details->>'offer_id')::bigint
            ORDER BY pa2.created_at DESC
            LIMIT 1
        ) pa ON true
        WHERE ae.id = :id
          AND ae.workspace_id = :workspaceId
          AND ae.details->>'mismatch_type' IS NOT NULL
        """;
    var params = new MapSqlParameterSource()
        .addValue("id", id)
        .addValue("workspaceId", workspaceId);

    List<MismatchDetailRow> rows = jdbc.query(sql, params, this::mapDetailRow);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public int autoResolveClearedPrice(long workspaceId, BigDecimal thresholdPct) {
    String sql = """
        UPDATE alert_event
        SET status = 'AUTO_RESOLVED',
            resolved_at = NOW(),
            resolved_reason = 'AUTO_RESOLVED'
        WHERE workspace_id = :workspaceId
          AND status = 'OPEN'
          AND details->>'mismatch_type' = 'PRICE'
          AND NOT EXISTS (
              SELECT 1
              FROM canonical_price_current cpc
              JOIN marketplace_offer mo ON mo.id = cpc.marketplace_offer_id
              JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
              JOIN LATERAL (
                  SELECT pa.target_price
                  FROM price_action pa
                  WHERE pa.marketplace_offer_id = mo.id
                    AND pa.status = 'SUCCEEDED'
                    AND pa.execution_mode = 'LIVE'
                  ORDER BY pa.created_at DESC
                  LIMIT 1
              ) latest_pa ON true
              WHERE mo.id = (alert_event.details->>'offer_id')::bigint
                AND mc.workspace_id = :workspaceId
                AND ABS(cpc.price - latest_pa.target_price)
                    / NULLIF(latest_pa.target_price, 0) * 100 > :thresholdPct
          )
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("thresholdPct", thresholdPct);
    return jdbc.update(sql, params);
  }

  public int autoResolveClearedPromo(long workspaceId) {
    String sql = """
        UPDATE alert_event
        SET status = 'AUTO_RESOLVED',
            resolved_at = NOW(),
            resolved_reason = 'AUTO_RESOLVED'
        WHERE workspace_id = :workspaceId
          AND status = 'OPEN'
          AND details->>'mismatch_type' = 'PROMO'
          AND NOT EXISTS (
              SELECT 1
              FROM canonical_promo_product cpp
              JOIN marketplace_offer mo ON mo.id = cpp.marketplace_offer_id
              WHERE mo.id = (alert_event.details->>'offer_id')::bigint
                AND cpp.participation_status != (alert_event.details->>'expected_value')
          )
        """;
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.update(sql, params);
  }

  public int autoResolveClearedStock(
      long workspaceId, int absoluteThreshold, double percentThreshold) {
    String sql = """
        UPDATE alert_event
        SET status = 'AUTO_RESOLVED',
            resolved_at = NOW(),
            resolved_reason = 'AUTO_RESOLVED'
        WHERE workspace_id = :workspaceId
          AND status = 'OPEN'
          AND details->>'mismatch_type' = 'STOCK'
          AND details->>'offer_id' IS NOT NULL
          AND details->>'expected_value' IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM (
                  SELECT
                      (
                          SELECT COALESCE(SUM(csc.available), 0)
                          FROM canonical_stock_current csc
                          WHERE csc.marketplace_offer_id
                              = (alert_event.details->>'offer_id')::bigint
                      ) AS total_avail,
                      (alert_event.details->>'expected_value')::int AS expected_ch
              ) calc
              WHERE ABS(calc.total_avail - calc.expected_ch) > :absoluteThreshold
                 OR (
                     CASE WHEN calc.expected_ch = 0 THEN 100.0
                          ELSE 100.0 * ABS(calc.total_avail - calc.expected_ch)
                              / NULLIF(calc.expected_ch::double precision, 0)
                     END
                 ) > :percentThreshold
          )
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("absoluteThreshold", absoluteThreshold)
        .addValue("percentThreshold", percentThreshold);
    return jdbc.update(sql, params);
  }

  public int autoResolveClearedFinance(long workspaceId, int gapHoursThreshold) {
    String sql = """
        UPDATE alert_event
        SET status = 'AUTO_RESOLVED',
            resolved_at = NOW(),
            resolved_reason = 'AUTO_RESOLVED'
        WHERE workspace_id = :workspaceId
          AND status = 'OPEN'
          AND details->>'mismatch_type' = 'FINANCE'
          AND NOT EXISTS (
              SELECT 1
              FROM marketplace_connection mc2
              WHERE mc2.id = alert_event.connection_id
                AND mc2.status = 'ACTIVE'
                AND NOT EXISTS (
                    SELECT 1 FROM canonical_finance_entry cfe
                    WHERE cfe.connection_id = mc2.id
                      AND cfe.entry_date >= CURRENT_DATE - make_interval(hours => :gapHours)
                )
          )
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("gapHours", gapHoursThreshold);
    return jdbc.update(sql, params);
  }

  public List<MismatchRow> findAllForExport(long workspaceId, MismatchFilter filter) {
    var where = new StringBuilder(" WHERE ae.workspace_id = :workspaceId AND ")
        .append(MISMATCH_CONDITION);
    var params = new MapSqlParameterSource("workspaceId", workspaceId);

    appendFilters(filter, where, params);

    String sql = BASE_SELECT + FROM_JOINS + where + " ORDER BY ae.opened_at DESC LIMIT 10000";
    return jdbc.query(sql, params, this::mapRow);
  }

  public boolean existsOpenMismatch(long workspaceId, long offerId, String mismatchType) {
    String sql = """
        SELECT EXISTS (
            SELECT 1 FROM alert_event
            WHERE workspace_id = :workspaceId
              AND status IN ('OPEN', 'ACKNOWLEDGED')
              AND details->>'mismatch_type' = :mismatchType
              AND (details->>'offer_id')::bigint = :offerId
        )
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("offerId", offerId)
        .addValue("mismatchType", mismatchType);
    return Boolean.TRUE.equals(jdbc.queryForObject(sql, params, Boolean.class));
  }

  public boolean existsOpenMismatchByConnection(long workspaceId, long connectionId,
                                                 String mismatchType) {
    String sql = """
        SELECT EXISTS (
            SELECT 1 FROM alert_event
            WHERE workspace_id = :workspaceId
              AND status IN ('OPEN', 'ACKNOWLEDGED')
              AND details->>'mismatch_type' = :mismatchType
              AND connection_id = :connectionId
              AND details->>'offer_id' IS NULL
        )
        """;
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("connectionId", connectionId)
        .addValue("mismatchType", mismatchType);
    return Boolean.TRUE.equals(jdbc.queryForObject(sql, params, Boolean.class));
  }

  public record SummaryData(
      long activeCount,
      long activeLast7d,
      long activePrev7d,
      long criticalCount,
      long criticalLast7d,
      long criticalPrev7d,
      BigDecimal avgHoursUnresolved,
      long autoResolvedToday,
      long autoResolvedYesterday
  ) {
  }

  private void appendFilters(MismatchFilter filter, StringBuilder where,
                             MapSqlParameterSource params) {
    if (filter == null) {
      return;
    }
    if (!CollectionUtils.isEmpty(filter.type())) {
      where.append(" AND ae.details->>'mismatch_type' IN (:types)");
      params.addValue("types", filter.type());
    }
    if (!CollectionUtils.isEmpty(filter.connectionId())) {
      where.append(" AND ae.connection_id IN (:connectionIds)");
      params.addValue("connectionIds", filter.connectionId());
    }
    if (!CollectionUtils.isEmpty(filter.status())) {
      List<String> dbStatuses = new ArrayList<>();
      boolean includeIgnored = false;
      for (String s : filter.status()) {
        if (MismatchStatus.IGNORED.equals(s)) {
          includeIgnored = true;
        } else {
          dbStatuses.add(MismatchStatus.toDb(s));
        }
      }
      if (includeIgnored && !dbStatuses.isEmpty()) {
        where.append(" AND (ae.status IN (:statuses) OR ")
            .append("(ae.status = 'RESOLVED' AND ae.resolved_reason LIKE 'IGNORED%'))");
        params.addValue("statuses", dbStatuses);
      } else if (includeIgnored) {
        where.append(" AND ae.status = 'RESOLVED' AND ae.resolved_reason LIKE 'IGNORED%'");
      } else if (!dbStatuses.isEmpty()) {
        where.append(" AND ae.status IN (:statuses)");
        params.addValue("statuses", dbStatuses);
      }
    }
    if (!CollectionUtils.isEmpty(filter.severity())) {
      where.append(" AND ae.severity IN (:severities)");
      params.addValue("severities", filter.severity());
    }
    if (filter.from() != null) {
      where.append(" AND ae.opened_at >= :dateFrom");
      params.addValue("dateFrom", filter.from().atStartOfDay());
    }
    if (filter.to() != null) {
      where.append(" AND ae.opened_at < :dateTo");
      params.addValue("dateTo", filter.to().plusDays(1).atStartOfDay());
    }
    if (StringUtils.hasText(filter.query())) {
      where.append(" AND (ae.details->>'sku_code' ILIKE :query")
          .append(" OR ae.details->>'offer_name' ILIKE :query)");
      params.addValue("query", "%" + filter.query().trim() + "%");
    }
    if (filter.offerId() != null) {
      where.append(" AND (ae.details->>'offer_id')::bigint = :offerId");
      params.addValue("offerId", filter.offerId());
    }
  }

  private String buildOrderByClause(Sort sort) {
    if (sort.isUnsorted()) {
      return " ORDER BY ae.opened_at DESC NULLS LAST";
    }
    var sb = new StringBuilder(" ORDER BY ");
    var orders = sort.stream().toList();
    for (int i = 0; i < orders.size(); i++) {
      Sort.Order order = orders.get(i);
      String column = SORT_COLUMNS.getOrDefault(
          order.getProperty(), "ae.opened_at");
      sb.append(column).append(" ").append(order.getDirection().name())
          .append(" NULLS LAST");
      if (i < orders.size() - 1) {
        sb.append(", ");
      }
    }
    return sb.toString();
  }

  private String buildResolvedReason(String resolution, String note) {
    if (StringUtils.hasText(note)) {
      return resolution + ": " + note;
    }
    return resolution;
  }

  private MismatchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
    String dbStatus = rs.getString("status");
    String resolvedReason = rs.getString("resolved_reason");
    return MismatchRow.builder()
        .alertEventId(rs.getLong("mismatch_id"))
        .mismatchType(rs.getString("mismatch_type"))
        .offerId(getBoxedLong(rs, "offer_id"))
        .offerName(rs.getString("offer_name"))
        .skuCode(rs.getString("sku_code"))
        .expectedValue(rs.getString("expected_value"))
        .actualValue(rs.getString("actual_value"))
        .deltaPct(rs.getBigDecimal("delta_pct"))
        .severity(rs.getString("severity"))
        .status(dbStatus)
        .resolvedReason(resolvedReason)
        .detectedAt(rs.getObject("detected_at", OffsetDateTime.class))
        .resolvedAt(rs.getObject("resolved_at", OffsetDateTime.class))
        .connectionName(rs.getString("connection_name"))
        .marketplaceType(rs.getString("marketplace_type"))
        .relatedActionId(getBoxedLong(rs, "related_action_id"))
        .build();
  }

  private MismatchDetailRow mapDetailRow(ResultSet rs, int rowNum) throws SQLException {
    return MismatchDetailRow.builder()
        .alertEventId(rs.getLong("mismatch_id"))
        .mismatchType(rs.getString("mismatch_type"))
        .offerId(getBoxedLong(rs, "offer_id"))
        .offerName(rs.getString("offer_name"))
        .skuCode(rs.getString("sku_code"))
        .expectedValue(rs.getString("expected_value"))
        .actualValue(rs.getString("actual_value"))
        .deltaPct(rs.getBigDecimal("delta_pct"))
        .severity(rs.getString("severity"))
        .status(rs.getString("status"))
        .resolvedReason(rs.getString("resolved_reason"))
        .detectedAt(rs.getObject("detected_at", OffsetDateTime.class))
        .connectionName(rs.getString("connection_name"))
        .marketplaceType(rs.getString("marketplace_type"))
        .connectionId(getBoxedLong(rs, "connection_id"))
        .acknowledgedAt(rs.getObject("acknowledged_at", OffsetDateTime.class))
        .acknowledgedByName(rs.getString("acknowledged_by_name"))
        .resolvedAt(rs.getObject("resolved_at", OffsetDateTime.class))
        .resolvedByName(rs.getString("resolved_by_name"))
        .relatedActionId(getBoxedLong(rs, "related_action_id"))
        .relatedActionStatus(rs.getString("related_action_status"))
        .relatedActionTargetPrice(rs.getBigDecimal("related_action_target_price"))
        .relatedActionExecutedAt(rs.getObject("related_action_executed_at", OffsetDateTime.class))
        .relatedActionReconciliationSource(rs.getString("related_action_reconciliation_source"))
        .build();
  }

  private Long getBoxedLong(ResultSet rs, String column) throws SQLException {
    long val = rs.getLong(column);
    return rs.wasNull() ? null : val;
  }
}
