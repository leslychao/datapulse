package io.datapulse.sellerops.persistence;

import io.datapulse.sellerops.api.MismatchFilter;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MismatchJdbcRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String BASE_SELECT = """
      SELECT
          ae.id                                     AS mismatch_id,
          ae.details->>'mismatch_type'              AS mismatch_type,
          (ae.details->>'offer_id')::bigint          AS offer_id,
          ae.details->>'offer_name'                  AS offer_name,
          ae.details->>'sku_code'                    AS sku_code,
          ae.details->>'expected_value'              AS expected_value,
          ae.details->>'actual_value'                AS actual_value,
          (ae.details->>'delta_pct')::numeric        AS delta_pct,
          ae.severity,
          ae.status,
          ae.opened_at                               AS detected_at,
          mc.name                                    AS connection_name
      """;

  private static final String FROM_JOINS = """
      FROM alert_event ae
      LEFT JOIN marketplace_connection mc ON mc.id = ae.connection_id
      """;

  private static final String MISMATCH_CONDITION =
      " ae.details->>'mismatch_type' IS NOT NULL";

  private static final Map<String, String> SORT_COLUMNS = Map.of(
      "detectedAt", "ae.opened_at",
      "severity", "ae.severity",
      "status", "ae.status",
      "type", "ae.details->>'mismatch_type'",
      "deltaPct", "(ae.details->>'delta_pct')::numeric",
      "offerName", "ae.details->>'offer_name'"
  );

  public Page<MismatchRow> findAll(long workspaceId, MismatchFilter filter,
                                   Pageable pageable) {
    var where = new StringBuilder(" WHERE ae.workspace_id = :workspaceId AND ")
        .append(MISMATCH_CONDITION);
    var params = new MapSqlParameterSource("workspaceId", workspaceId);

    appendFilters(filter, where, params);

    String countSql = "SELECT COUNT(*) " + FROM_JOINS + where;
    Long total = jdbc.queryForObject(countSql, params, Long.class);
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

  public int resolve(long id, long workspaceId, String resolvedReason) {
    String sql = """
        UPDATE alert_event
        SET status = 'RESOLVED',
            resolved_at = NOW(),
            resolved_reason = :resolvedReason
        WHERE id = :id
          AND workspace_id = :workspaceId
          AND status = 'ACKNOWLEDGED'
        """;
    var params = new MapSqlParameterSource()
        .addValue("id", id)
        .addValue("workspaceId", workspaceId)
        .addValue("resolvedReason", resolvedReason);
    return jdbc.update(sql, params);
  }

  public MismatchStatusCounts countByStatus(long workspaceId) {
    String sql = """
        SELECT
            COUNT(*)                                        AS total,
            COUNT(*) FILTER (WHERE ae.status = 'OPEN')      AS open,
            COUNT(*) FILTER (WHERE ae.status = 'ACKNOWLEDGED') AS acknowledged,
            COUNT(*) FILTER (WHERE ae.status = 'RESOLVED')  AS resolved
        """ + FROM_JOINS
        + " WHERE ae.workspace_id = :workspaceId AND " + MISMATCH_CONDITION;
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.queryForObject(sql, params, (rs, rowNum) ->
        new MismatchStatusCounts(
            rs.getLong("total"),
            rs.getLong("open"),
            rs.getLong("acknowledged"),
            rs.getLong("resolved")
        ));
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
            ae.opened_at                                AS detected_at,
            mc.name                                     AS connection_name,
            mc.marketplace_type                         AS marketplace_type,
            mc.id                                       AS connection_id,
            ae.acknowledged_at,
            ack_user.name                               AS acknowledged_by_name,
            ae.resolved_at,
            ae.resolved_reason,
            pa.id                                       AS related_action_id,
            pa.status                                   AS related_action_status,
            pa.target_price                             AS related_action_target_price,
            pa.updated_at                               AS related_action_executed_at,
            pa.reconciliation_source                    AS related_action_reconciliation_source
        FROM alert_event ae
        LEFT JOIN marketplace_connection mc ON mc.id = ae.connection_id
        LEFT JOIN app_user ack_user ON ack_user.id = ae.acknowledged_by
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

  public int autoResolveCleared(long workspaceId) {
    String sql = """
        UPDATE alert_event
        SET status = 'AUTO_RESOLVED',
            resolved_at = NOW(),
            resolved_reason = 'AUTO_RESOLVED'
        WHERE workspace_id = :workspaceId
          AND status = 'OPEN'
          AND details->>'mismatch_type' IS NOT NULL
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
                AND alert_event.details->>'mismatch_type' = 'PRICE'
                AND ABS(cpc.price - latest_pa.target_price)
                    / NULLIF(latest_pa.target_price, 0) * 100 > 1
          )
          AND details->>'mismatch_type' = 'PRICE'
        """;
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.update(sql, params);
  }

  public record MismatchStatusCounts(long total, long open, long acknowledged, long resolved) {
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
      where.append(" AND ae.status IN (:statuses)");
      params.addValue("statuses", filter.status());
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

  private MismatchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
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
        .status(rs.getString("status"))
        .detectedAt(rs.getObject("detected_at", OffsetDateTime.class))
        .connectionName(rs.getString("connection_name"))
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
        .detectedAt(rs.getObject("detected_at", OffsetDateTime.class))
        .connectionName(rs.getString("connection_name"))
        .marketplaceType(rs.getString("marketplace_type"))
        .connectionId(getBoxedLong(rs, "connection_id"))
        .acknowledgedAt(rs.getObject("acknowledged_at", OffsetDateTime.class))
        .acknowledgedByName(rs.getString("acknowledged_by_name"))
        .resolvedAt(rs.getObject("resolved_at", OffsetDateTime.class))
        .resolvedReason(rs.getString("resolved_reason"))
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
