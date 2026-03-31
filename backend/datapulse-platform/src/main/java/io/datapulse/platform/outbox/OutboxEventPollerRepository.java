package io.datapulse.platform.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventPollerRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_FOR_PUBLISH = """
            SELECT id, event_type, aggregate_type, aggregate_id, payload,
                   status, created_at, published_at, retry_count, next_retry_at
            FROM outbox_event
            WHERE (status = 'PENDING'
                   OR (status = 'FAILED' AND retry_count < :maxRetry AND next_retry_at <= now()))
              AND event_type IN (:types)
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    private static final String UPDATE_PUBLISHED = """
            UPDATE outbox_event
            SET status = 'PUBLISHED', published_at = now()
            WHERE id IN (:ids)
            """;

    private static final String UPDATE_FAILED = """
            UPDATE outbox_event
            SET status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_at = :nextRetryAt
            WHERE id = :id
            """;

    public List<OutboxEvent> findEventsForPublish(List<String> eventTypes, int limit, int maxRetry) {
        var params = new MapSqlParameterSource()
                .addValue("types", eventTypes)
                .addValue("limit", limit)
                .addValue("maxRetry", maxRetry);

        return jdbc.query(SELECT_FOR_PUBLISH, params, this::mapRow);
    }

    public void markPublished(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update(UPDATE_PUBLISHED, new MapSqlParameterSource("ids", ids));
    }

    public void markFailed(long id, OffsetDateTime nextRetryAt) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nextRetryAt", nextRetryAt);

        jdbc.update(UPDATE_FAILED, params);
    }

    private OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        var event = new OutboxEvent();
        event.setId(rs.getLong("id"));
        event.setEventType(OutboxEventType.valueOf(rs.getString("event_type")));
        event.setAggregateType(rs.getString("aggregate_type"));
        event.setAggregateId(rs.getLong("aggregate_id"));
        event.setPayload(rs.getString("payload"));
        event.setStatus(OutboxEventStatus.valueOf(rs.getString("status")));
        event.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        event.setPublishedAt(rs.getObject("published_at", OffsetDateTime.class));
        event.setRetryCount(rs.getInt("retry_count"));
        event.setNextRetryAt(rs.getObject("next_retry_at", OffsetDateTime.class));
        return event;
    }
}
