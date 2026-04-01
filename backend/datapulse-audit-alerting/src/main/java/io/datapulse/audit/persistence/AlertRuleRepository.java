package io.datapulse.audit.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import io.datapulse.audit.api.AlertRuleResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AlertRuleRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_COLUMNS = """
            SELECT id, workspace_id, rule_type, target_entity_type, target_entity_id,
                   config, enabled, severity, blocks_automation, created_at, updated_at
            FROM alert_rule
            """;

    private static final String UPDATE = """
            UPDATE alert_rule
            SET config = :config::jsonb,
                enabled = :enabled,
                severity = :severity,
                blocks_automation = :blocksAutomation,
                updated_at = now()
            WHERE id = :id AND workspace_id = :workspaceId
            """;

    private static final String UPDATE_ENABLED = """
            UPDATE alert_rule
            SET enabled = :enabled, updated_at = now()
            WHERE id = :id AND workspace_id = :workspaceId
            """;

    public List<AlertRuleResponse> findByWorkspaceId(long workspaceId) {
        String sql = SELECT_COLUMNS + " WHERE workspace_id = :workspaceId ORDER BY rule_type, id";
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return jdbc.query(sql, params, this::mapRow);
    }

    public Optional<AlertRuleResponse> findById(long id, long workspaceId) {
        String sql = SELECT_COLUMNS + " WHERE id = :id AND workspace_id = :workspaceId";
        var params = new MapSqlParameterSource("id", id)
                .addValue("workspaceId", workspaceId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    public List<AlertRuleResponse> findEnabledByRuleType(String ruleType) {
        String sql = SELECT_COLUMNS + " WHERE rule_type = :ruleType AND enabled = true";
        var params = new MapSqlParameterSource("ruleType", ruleType);
        return jdbc.query(sql, params, this::mapRow);
    }

    public List<AlertRuleResponse> findEnabledByWorkspaceAndRuleType(long workspaceId, String ruleType) {
        String sql = SELECT_COLUMNS
                + " WHERE workspace_id = :workspaceId AND rule_type = :ruleType AND enabled = true";
        var params = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("ruleType", ruleType);
        return jdbc.query(sql, params, this::mapRow);
    }

    public int update(long id, long workspaceId, String config, boolean enabled,
                      String severity, boolean blocksAutomation) {
        var params = new MapSqlParameterSource("id", id)
                .addValue("workspaceId", workspaceId)
                .addValue("config", config)
                .addValue("enabled", enabled)
                .addValue("severity", severity)
                .addValue("blocksAutomation", blocksAutomation);
        return jdbc.update(UPDATE, params);
    }

    public int setEnabled(long id, long workspaceId, boolean enabled) {
        var params = new MapSqlParameterSource("id", id)
                .addValue("workspaceId", workspaceId)
                .addValue("enabled", enabled);
        return jdbc.update(UPDATE_ENABLED, params);
    }

    private AlertRuleResponse mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AlertRuleResponse(
                rs.getLong("id"),
                rs.getLong("workspace_id"),
                rs.getString("rule_type"),
                rs.getString("target_entity_type"),
                rs.getObject("target_entity_id", Long.class),
                rs.getString("config"),
                rs.getBoolean("enabled"),
                rs.getString("severity"),
                rs.getBoolean("blocks_automation"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
