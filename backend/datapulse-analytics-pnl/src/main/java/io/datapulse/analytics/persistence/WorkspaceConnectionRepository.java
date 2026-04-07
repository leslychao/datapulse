package io.datapulse.analytics.persistence;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkspaceConnectionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FIND_IDS_SQL = """
            SELECT id FROM marketplace_connection
            WHERE workspace_id = :workspaceId
            """;

    public List<Long> findConnectionIdsByWorkspaceId(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return jdbc.queryForList(FIND_IDS_SQL, params, Long.class);
    }
}
