package io.datapulse.etl.persistence.canonical;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductMasterLookupRepository {

  private final JdbcTemplate jdbc;

  private static final String FIND_ALL_BY_WORKSPACE = """
      SELECT external_code, id
      FROM product_master
      WHERE workspace_id = ?
      """;

  public Map<String, Long> findAllByWorkspace(long workspaceId) {
    return jdbc.query(FIND_ALL_BY_WORKSPACE,
            (rs, rowNum) -> Map.entry(
                rs.getString("external_code"),
                rs.getLong("id")),
            workspaceId)
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
