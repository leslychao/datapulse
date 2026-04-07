package io.datapulse.analytics.persistence;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkspaceConnectionRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String FIND_ACTIVE_SQL = """
      SELECT id, name, marketplace_type
      FROM marketplace_connection
      WHERE workspace_id = :workspaceId
        AND status != 'ARCHIVED'
      """;

  public List<ConnectionRow> findActiveByWorkspaceId(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.query(FIND_ACTIVE_SQL, params, (rs, rowNum) -> new ConnectionRow(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("marketplace_type")
    ));
  }

  public List<Long> findConnectionIdsByWorkspaceId(long workspaceId) {
    return findActiveByWorkspaceId(workspaceId).stream()
        .map(ConnectionRow::id)
        .toList();
  }

  public Map<Long, ConnectionRow> findActiveByWorkspaceIdAsMap(long workspaceId) {
    return findActiveByWorkspaceId(workspaceId).stream()
        .collect(Collectors.toMap(ConnectionRow::id, r -> r));
  }

  public record ConnectionRow(long id, String name, String marketplaceType) {}
}
