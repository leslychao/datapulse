package io.datapulse.audit.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.datapulse.audit.api.AuditLogFilter;
import io.datapulse.audit.api.AuditLogResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AuditLogRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT = """
            INSERT INTO audit_log (workspace_id, actor_type, actor_user_id, action_type,
                                   entity_type, entity_id, outcome, details,
                                   ip_address, correlation_id)
            VALUES (:workspaceId, :actorType, :actorUserId, :actionType,
                    :entityType, :entityId, :outcome, :details::jsonb,
                    :ipAddress::inet, :correlationId::uuid)
            """;

    private static final String BASE_WHERE = """
            FROM audit_log
            WHERE workspace_id = :workspaceId
            """;

    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "createdAt", "created_at",
            "actionType", "action_type",
            "entityType", "entity_type"
    );

    public void insert(long workspaceId, String actorType, Long actorUserId,
                        String actionType, String entityType, String entityId,
                        String outcome, String details, String ipAddress,
                        String correlationId) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("actorType", actorType)
                .addValue("actorUserId", actorUserId)
                .addValue("actionType", actionType)
                .addValue("entityType", entityType)
                .addValue("entityId", entityId)
                .addValue("outcome", outcome)
                .addValue("details", details)
                .addValue("ipAddress", ipAddress)
                .addValue("correlationId", correlationId);
        jdbc.update(INSERT, params);
    }

    public List<AuditLogResponse> findAll(long workspaceId, AuditLogFilter filter,
                                          String sortColumn, int limit, long offset) {
        var params = buildFilterParams(workspaceId, filter);
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String orderBy = SORT_WHITELIST.getOrDefault(sortColumn, "created_at");

        String sql = "SELECT id, workspace_id, actor_type, actor_user_id, action_type, "
                + "entity_type, entity_id, outcome, details, ip_address, "
                + "correlation_id, created_at "
                + BASE_WHERE
                + buildFilterClause(filter)
                + " ORDER BY " + orderBy + " DESC NULLS LAST"
                + " LIMIT :limit OFFSET :offset";

        return jdbc.query(sql, params, this::mapRow);
    }

    public long count(long workspaceId, AuditLogFilter filter) {
        var params = buildFilterParams(workspaceId, filter);

        String sql = "SELECT count(*) " + BASE_WHERE + buildFilterClause(filter);

        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    private MapSqlParameterSource buildFilterParams(long workspaceId, AuditLogFilter filter) {
        var params = new MapSqlParameterSource().addValue("workspaceId", workspaceId);
        if (filter.entityType() != null && !filter.entityType().isBlank()) {
            params.addValue("entityType", filter.entityType().trim());
        }
        if (filter.entityId() != null && !filter.entityId().isBlank()) {
            params.addValue("entityId", filter.entityId().trim());
        }
        if (filter.actorUserId() != null) {
            params.addValue("actorUserId", filter.actorUserId());
        }
        if (filter.actionType() != null && !filter.actionType().isBlank()) {
            params.addValue("actionType", filter.actionType().trim());
        }
        if (filter.dateFrom() != null) {
            params.addValue("dateFrom", filter.dateFrom());
        }
        if (filter.dateTo() != null) {
            params.addValue("dateTo", filter.dateTo().plusDays(1));
        }
        return params;
    }

    private String buildFilterClause(AuditLogFilter filter) {
        var sb = new StringBuilder();
        if (filter.entityType() != null && !filter.entityType().isBlank()) {
            sb.append(" AND entity_type = :entityType");
        }
        if (filter.entityId() != null && !filter.entityId().isBlank()) {
            sb.append(" AND entity_id = :entityId");
        }
        if (filter.actorUserId() != null) {
            sb.append(" AND actor_user_id = :actorUserId");
        }
        if (filter.actionType() != null && !filter.actionType().isBlank()) {
            sb.append(" AND action_type = :actionType");
        }
        if (filter.dateFrom() != null) {
            sb.append(" AND created_at >= :dateFrom");
        }
        if (filter.dateTo() != null) {
            sb.append(" AND created_at < :dateTo");
        }
        return sb.toString();
    }

    private AuditLogResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogResponse(
                rs.getLong("id"),
                rs.getLong("workspace_id"),
                rs.getString("actor_type"),
                rs.getObject("actor_user_id", Long.class),
                rs.getString("action_type"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("outcome"),
                rs.getString("details"),
                rs.getString("ip_address"),
                rs.getString("correlation_id"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
