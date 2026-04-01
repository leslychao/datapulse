package io.datapulse.audit.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import io.datapulse.audit.api.NotificationResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserNotificationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT = """
            INSERT INTO user_notification (workspace_id, user_id, alert_event_id,
                                           notification_type, title, body, severity)
            VALUES (:workspaceId, :userId, :alertEventId,
                    :notificationType, :title, :body, :severity)
            """;

    private static final String SELECT_COLUMNS = """
            SELECT id, workspace_id, user_id, alert_event_id, notification_type,
                   title, body, severity, read_at, created_at
            FROM user_notification
            """;

    public long insert(long workspaceId, long userId, Long alertEventId,
                       String notificationType, String title, String body,
                       String severity) {
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("userId", userId)
                .addValue("alertEventId", alertEventId)
                .addValue("notificationType", notificationType)
                .addValue("title", title)
                .addValue("body", body)
                .addValue("severity", severity);

        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(INSERT, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain generated key for user_notification");
        }
        return key.longValue();
    }

    public List<NotificationResponse> findByUserAndWorkspace(long userId, long workspaceId,
                                                             int limit, long offset) {
        String sql = SELECT_COLUMNS
                + " WHERE user_id = :userId AND workspace_id = :workspaceId"
                + " ORDER BY created_at DESC"
                + " LIMIT :limit OFFSET :offset";
        var params = new MapSqlParameterSource("userId", userId)
                .addValue("workspaceId", workspaceId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, this::mapRow);
    }

    public long countByUserAndWorkspace(long userId, long workspaceId) {
        String sql = "SELECT count(*) FROM user_notification"
                + " WHERE user_id = :userId AND workspace_id = :workspaceId";
        var params = new MapSqlParameterSource("userId", userId)
                .addValue("workspaceId", workspaceId);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    public long countUnread(long userId, long workspaceId) {
        String sql = "SELECT count(*) FROM user_notification"
                + " WHERE user_id = :userId AND workspace_id = :workspaceId AND read_at IS NULL";
        var params = new MapSqlParameterSource("userId", userId)
                .addValue("workspaceId", workspaceId);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    public int markRead(long id, long userId, long workspaceId) {
        String sql = """
                UPDATE user_notification
                SET read_at = now()
                WHERE id = :id AND user_id = :userId AND workspace_id = :workspaceId AND read_at IS NULL
                """;
        var params = new MapSqlParameterSource("id", id)
                .addValue("userId", userId)
                .addValue("workspaceId", workspaceId);
        return jdbc.update(sql, params);
    }

    public int markAllRead(long userId, long workspaceId) {
        String sql = """
                UPDATE user_notification
                SET read_at = now()
                WHERE user_id = :userId AND workspace_id = :workspaceId AND read_at IS NULL
                """;
        var params = new MapSqlParameterSource("userId", userId)
                .addValue("workspaceId", workspaceId);
        return jdbc.update(sql, params);
    }

    /**
     * Returns user IDs of active workspace members with the given roles.
     * Used for notification fan-out: only members at or above required role receive notifications.
     */
    public List<Long> findMemberUserIds(long workspaceId, List<String> roles) {
        String sql = """
                SELECT wm.user_id
                FROM workspace_member wm
                WHERE wm.workspace_id = :workspaceId
                  AND wm.status = 'ACTIVE'
                  AND wm.role IN (:roles)
                """;
        var params = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("roles", roles);
        return jdbc.queryForList(sql, params, Long.class);
    }

    private NotificationResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationResponse(
                rs.getLong("id"),
                rs.getLong("workspace_id"),
                rs.getLong("user_id"),
                rs.getObject("alert_event_id", Long.class),
                rs.getString("notification_type"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("severity"),
                rs.getObject("read_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
