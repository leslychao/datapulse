package io.datapulse.analytics.persistence;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SyncStateReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SYNC_FRESHNESS_SQL = """
      SELECT
          mc.id AS connection_id,
          mc.name AS connection_name,
          mc.marketplace_type AS source_platform,
          mss.data_domain,
          mss.last_success_at
      FROM marketplace_sync_state mss
      JOIN marketplace_connection mc ON mss.marketplace_connection_id = mc.id
      WHERE mc.workspace_id = :workspaceId
        AND mc.status != 'ARCHIVED'
      ORDER BY mc.id, mss.data_domain
      """;

  public List<SyncFreshnessRow> findSyncFreshness(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);

    return jdbc.query(SYNC_FRESHNESS_SQL, params, (rs, rowNum) -> new SyncFreshnessRow(
        rs.getLong("connection_id"),
        rs.getString("connection_name"),
        rs.getString("source_platform"),
        rs.getString("data_domain"),
        rs.getObject("last_success_at", OffsetDateTime.class)
    ));
  }

  public record SyncFreshnessRow(
      long connectionId,
      String connectionName,
      String sourcePlatform,
      String dataDomain,
      OffsetDateTime lastSuccessAt
  ) {}
}
