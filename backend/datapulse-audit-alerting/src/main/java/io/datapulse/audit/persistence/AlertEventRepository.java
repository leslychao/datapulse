package io.datapulse.audit.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.datapulse.audit.api.AlertEventFilter;
import io.datapulse.audit.api.AlertEventResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AlertEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT = """
            INSERT INTO alert_event (alert_rule_id, workspace_id, connection_id,
                                     status, severity, title, details, blocks_automation)
            VALUES (:alertRuleId, :workspaceId, :connectionId,
                    :status, :severity, :title, :details::jsonb, :blocksAutomation)
            """;

    private static final String SELECT_COLUMNS = """
            SELECT id, alert_rule_id, workspace_id, connection_id, status, severity,
                   title, details, blocks_automation, opened_at,
                   acknowledged_at, acknowledged_by, resolved_at, resolved_reason
            FROM alert_event
            """;

    private static final String BASE_WHERE = " WHERE workspace_id = :workspaceId";

    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "openedAt", "opened_at",
            "severity", "severity",
            "status", "status"
    );

    public long insertEventDriven(long workspaceId, Long connectionId,
                                  String severity, String title,
                                  String details, boolean blocksAutomation) {
        return doInsert(null, workspaceId, connectionId, severity, title, details, blocksAutomation);
    }

    public long insertRuleBased(long alertRuleId, long workspaceId, Long connectionId,
                                String severity, String title,
                                String details, boolean blocksAutomation) {
        return doInsert(alertRuleId, workspaceId, connectionId, severity, title, details, blocksAutomation);
    }

    public List<AlertEventResponse> findAll(long workspaceId, AlertEventFilter filter,
                                            String sortColumn, int limit, long offset) {
        var params = buildFilterParams(workspaceId, filter);
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String orderBy = SORT_WHITELIST.getOrDefault(sortColumn, "opened_at");
        String sql = SELECT_COLUMNS + BASE_WHERE
                + buildFilterClause(filter)
                + " ORDER BY " + orderBy + " DESC NULLS LAST"
                + " LIMIT :limit OFFSET :offset";

        return jdbc.query(sql, params, this::mapRow);
    }

    public long count(long workspaceId, AlertEventFilter filter) {
        var params = buildFilterParams(workspaceId, filter);
        String sql = "SELECT count(*) FROM alert_event" + BASE_WHERE + buildFilterClause(filter);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    public Optional<AlertEventResponse> findById(long id, long workspaceId) {
        String sql = SELECT_COLUMNS + " WHERE id = :id AND workspace_id = :workspaceId";
        var params = new MapSqlParameterSource("id", id).addValue("workspaceId", workspaceId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    public int acknowledge(long id, long workspaceId, long userId) {
        String sql = """
                UPDATE alert_event
                SET status = 'ACKNOWLEDGED', acknowledged_at = now(), acknowledged_by = :userId
                WHERE id = :id AND workspace_id = :workspaceId AND status = 'OPEN'
                """;
        var params = new MapSqlParameterSource("id", id)
                .addValue("workspaceId", workspaceId)
                .addValue("userId", userId);
        return jdbc.update(sql, params);
    }

    public int resolve(long id, long workspaceId) {
        String sql = """
                UPDATE alert_event
                SET status = 'RESOLVED', resolved_at = now(), resolved_reason = 'MANUAL'
                WHERE id = :id AND workspace_id = :workspaceId AND status = 'ACKNOWLEDGED'
                """;
        var params = new MapSqlParameterSource("id", id).addValue("workspaceId", workspaceId);
        return jdbc.update(sql, params);
    }

    public int autoResolve(long alertRuleId, long connectionId) {
        String sql = """
                UPDATE alert_event
                SET status = 'AUTO_RESOLVED', resolved_at = now(), resolved_reason = 'AUTO'
                WHERE alert_rule_id = :alertRuleId
                  AND connection_id = :connectionId
                  AND status IN ('OPEN', 'ACKNOWLEDGED')
                """;
        var params = new MapSqlParameterSource("alertRuleId", alertRuleId)
                .addValue("connectionId", connectionId);
        return jdbc.update(sql, params);
    }

    public List<AlertEventResponse> findActiveByRuleAndConnection(long alertRuleId, long connectionId) {
        String sql = SELECT_COLUMNS
                + " WHERE alert_rule_id = :alertRuleId AND connection_id = :connectionId"
                + " AND status IN ('OPEN', 'ACKNOWLEDGED')";
        var params = new MapSqlParameterSource("alertRuleId", alertRuleId)
                .addValue("connectionId", connectionId);
        return jdbc.query(sql, params, this::mapRow);
    }

    public List<AlertEventResponse> findActiveByRule(long alertRuleId) {
        String sql = SELECT_COLUMNS
                + " WHERE alert_rule_id = :alertRuleId AND status IN ('OPEN', 'ACKNOWLEDGED')";
        var params = new MapSqlParameterSource("alertRuleId", alertRuleId);
        return jdbc.query(sql, params, this::mapRow);
    }

    public boolean existsBlockingAlert(long workspaceId, long connectionId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM alert_event
                    WHERE workspace_id = :workspaceId
                      AND connection_id = :connectionId
                      AND blocks_automation = true
                      AND status IN ('OPEN', 'ACKNOWLEDGED')
                )
                """;
        var params = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("connectionId", connectionId);
        Boolean result = jdbc.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private long doInsert(Long alertRuleId, long workspaceId, Long connectionId,
                          String severity, String title, String details,
                          boolean blocksAutomation) {
        var params = new MapSqlParameterSource();
        params.addValue("alertRuleId", alertRuleId);
        params.addValue("workspaceId", workspaceId);
        params.addValue("connectionId", connectionId);
        params.addValue("status", "OPEN");
        params.addValue("severity", severity);
        params.addValue("title", title);
        params.addValue("details", details);
        params.addValue("blocksAutomation", blocksAutomation);

        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(INSERT, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain generated key for alert_event");
        }
        return key.longValue();
    }

    private MapSqlParameterSource buildFilterParams(long workspaceId, AlertEventFilter filter) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        if (filter.status() != null && !filter.status().isBlank()) {
            params.addValue("status", filter.status().trim());
        }
        if (filter.severity() != null && !filter.severity().isBlank()) {
            params.addValue("severity", filter.severity().trim());
        }
        if (filter.connectionId() != null) {
            params.addValue("connectionId", filter.connectionId());
        }
        return params;
    }

    private String buildFilterClause(AlertEventFilter filter) {
        var sb = new StringBuilder();
        if (filter.status() != null && !filter.status().isBlank()) {
            sb.append(" AND status = :status");
        }
        if (filter.severity() != null && !filter.severity().isBlank()) {
            sb.append(" AND severity = :severity");
        }
        if (filter.connectionId() != null) {
            sb.append(" AND connection_id = :connectionId");
        }
        return sb.toString();
    }

    private AlertEventResponse mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AlertEventResponse(
                rs.getLong("id"),
                rs.getObject("alert_rule_id", Long.class),
                rs.getLong("workspace_id"),
                rs.getObject("connection_id", Long.class),
                rs.getString("status"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("details"),
                rs.getBoolean("blocks_automation"),
                rs.getObject("opened_at", OffsetDateTime.class),
                rs.getObject("acknowledged_at", OffsetDateTime.class),
                rs.getObject("acknowledged_by", Long.class),
                rs.getObject("resolved_at", OffsetDateTime.class),
                rs.getString("resolved_reason")
        );
    }
}
