package io.datapulse.audit.persistence;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * Inserts an event-driven alert (alert_rule_id = NULL).
     *
     * @return generated alert_event id
     */
    public long insertEventDriven(long workspaceId, Long connectionId,
                                  String severity, String title,
                                  String details, boolean blocksAutomation) {
        var params = new MapSqlParameterSource();
        params.addValue("alertRuleId", null);
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
}
