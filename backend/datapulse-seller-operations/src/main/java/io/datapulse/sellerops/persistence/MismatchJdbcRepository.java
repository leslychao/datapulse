package io.datapulse.sellerops.persistence;

import io.datapulse.sellerops.api.MismatchFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
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

    String querySql = BASE_SELECT + FROM_JOINS + where
        + " ORDER BY ae.opened_at DESC LIMIT :limit OFFSET :offset";
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

  public record MismatchStatusCounts(long total, long open, long acknowledged, long resolved) {
  }

  private void appendFilters(MismatchFilter filter, StringBuilder where,
                             MapSqlParameterSource params) {
    if (filter == null) {
      return;
    }
    if (StringUtils.hasText(filter.type())) {
      where.append(" AND ae.details->>'mismatch_type' = :mismatchType");
      params.addValue("mismatchType", filter.type());
    }
    if (filter.connectionId() != null) {
      where.append(" AND ae.connection_id = :connectionId");
      params.addValue("connectionId", filter.connectionId());
    }
    if (StringUtils.hasText(filter.severity())) {
      where.append(" AND ae.severity = :severity");
      params.addValue("severity", filter.severity());
    }
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

  private Long getBoxedLong(ResultSet rs, String column) throws SQLException {
    long val = rs.getLong(column);
    return rs.wasNull() ? null : val;
  }
}
