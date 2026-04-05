package io.datapulse.analytics.domain.materializer.dim;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.config.AnalyticsProperties;
import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Materializes dim_advertising_campaign from canonical_advertising_campaign (PostgreSQL)
 * into ClickHouse. One row per campaign per connection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DimAdvertisingCampaignMaterializer implements AnalyticsMaterializer {

  private static final String TABLE = "dim_advertising_campaign";

  private static final String PG_QUERY = """
      SELECT cac.connection_id,
             mc.marketplace_type AS source_platform,
             cac.external_campaign_id AS campaign_id,
             cac.name,
             cac.campaign_type,
             cac.status,
             cac.placement,
             cac.daily_budget,
             cac.start_time,
             cac.end_time,
             cac.created_at_external AS created_at
      FROM canonical_advertising_campaign cac
      JOIN marketplace_connection mc ON cac.connection_id = mc.id
      ORDER BY cac.id
      LIMIT :limit OFFSET :offset
      """;

  private static final String CH_INSERT = """
      INSERT INTO %s
      (connection_id, source_platform, campaign_id, name, campaign_type,
       status, placement, daily_budget, start_time, end_time, created_at, ver)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private final MaterializationJdbc jdbc;
  private final AnalyticsProperties properties;

  @Override
  public void materializeFull() {
    long ver = Instant.now().toEpochMilli();
    final int[] total = {0};

    jdbc.fullMaterializeWithSwap(TABLE, staging -> {
      String chInsert = CH_INSERT.formatted(staging);
      int offset = 0;

      while (true) {
        List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_QUERY,
            Map.of("limit", properties.batchSize(), "offset", offset));
        if (rows.isEmpty()) {
          break;
        }

        jdbc.ch().batchUpdate(chInsert, rows, rows.size(), (ps, row) -> {
          ps.setInt(1, ((Number) row.get("connection_id")).intValue());
          ps.setString(2, (String) row.get("source_platform"));
          ps.setLong(3, Long.parseLong((String) row.get("campaign_id")));
          ps.setString(4, (String) row.get("name"));
          ps.setString(5, (String) row.get("campaign_type"));
          ps.setString(6, (String) row.get("status"));
          setNullableString(ps, 7, row.get("placement"));
          setNullableDecimal(ps, 8, row.get("daily_budget"));
          setNullableTimestamp(ps, 9, row.get("start_time"));
          setNullableTimestamp(ps, 10, row.get("end_time"));
          setNullableTimestamp(ps, 11, row.get("created_at"));
          ps.setLong(12, ver);
        });

        total[0] += rows.size();
        offset += properties.batchSize();
      }
    });

    log.info("Materialized dim_advertising_campaign: rows={}", total[0]);
  }

  @Override
  public void materializeIncremental(long jobExecutionId) {
    materializeFull();
  }

  @Override
  public String tableName() {
    return TABLE;
  }

  @Override
  public MaterializationPhase phase() {
    return MaterializationPhase.DIMENSION;
  }

  @Override
  public int order() {
    return 3;
  }

  private static void setNullableString(
      java.sql.PreparedStatement ps, int idx, Object value) throws java.sql.SQLException {
    if (value != null) {
      ps.setString(idx, (String) value);
    } else {
      ps.setNull(idx, Types.VARCHAR);
    }
  }

  private static void setNullableDecimal(
      java.sql.PreparedStatement ps, int idx, Object value) throws java.sql.SQLException {
    if (value instanceof BigDecimal bd) {
      ps.setBigDecimal(idx, bd);
    } else {
      ps.setNull(idx, Types.DECIMAL);
    }
  }

  private static void setNullableTimestamp(
      java.sql.PreparedStatement ps, int idx, Object value) throws java.sql.SQLException {
    if (value instanceof OffsetDateTime odt) {
      ps.setTimestamp(idx, Timestamp.from(odt.toInstant()));
    } else if (value instanceof Timestamp ts) {
      ps.setTimestamp(idx, ts);
    } else {
      ps.setNull(idx, Types.TIMESTAMP);
    }
  }
}
