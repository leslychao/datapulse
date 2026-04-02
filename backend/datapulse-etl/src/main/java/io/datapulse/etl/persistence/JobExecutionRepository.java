package io.datapulse.etl.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import io.datapulse.etl.domain.JobExecutionStatus;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JobExecutionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT = """
            INSERT INTO job_execution (connection_id, event_type, status)
            VALUES (:connectionId, :eventType, :status)
            """;

    private static final String CAS_STATUS = """
            UPDATE job_execution
            SET status = :target, started_at = CASE WHEN :target = 'IN_PROGRESS' AND started_at IS NULL THEN now() ELSE started_at END,
                completed_at = CASE WHEN :target IN ('COMPLETED','COMPLETED_WITH_ERRORS','FAILED','STALE') THEN now() ELSE completed_at END
            WHERE id = :id AND status = :expected
            """;

    private static final String UPDATE_CHECKPOINT = """
            UPDATE job_execution
            SET checkpoint = CAST(:checkpoint AS jsonb)
            WHERE id = :id
            """;

    private static final String UPDATE_ERROR_DETAILS = """
            UPDATE job_execution
            SET error_details = CAST(:errorDetails AS jsonb)
            WHERE id = :id
            """;

    private static final String FIND_BY_ID = """
            SELECT id, connection_id, event_type, status, started_at, completed_at,
                   error_details::text, checkpoint::text, created_at
            FROM job_execution
            WHERE id = :id
            """;

    private static final String EXISTS_ACTIVE_FOR_CONNECTION = """
            SELECT EXISTS(
                SELECT 1 FROM job_execution
                WHERE connection_id = :connectionId
                  AND status IN ('PENDING', 'IN_PROGRESS', 'MATERIALIZING', 'RETRY_SCHEDULED')
            )
            """;

    private static final String MARK_STALE_IN_PROGRESS = """
            UPDATE job_execution
            SET status = 'STALE', completed_at = now()
            WHERE status IN ('IN_PROGRESS', 'MATERIALIZING')
              AND started_at < :threshold
            """;

    private static final String MARK_STALE_RETRY_SCHEDULED = """
            UPDATE job_execution
            SET status = 'STALE', completed_at = now()
            WHERE status = 'RETRY_SCHEDULED'
              AND COALESCE(
                  (checkpoint->>'last_retry_at')::timestamptz,
                  started_at,
                  created_at
              ) < :threshold
            """;

    public long insert(long connectionId, String eventType) {
        var keyHolder = new GeneratedKeyHolder();
        var params = new MapSqlParameterSource()
                .addValue("connectionId", connectionId)
                .addValue("eventType", eventType)
                .addValue("status", JobExecutionStatus.PENDING.name());

        jdbc.update(INSERT, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    public boolean casStatus(long id, JobExecutionStatus expected, JobExecutionStatus target) {
        int updated = jdbc.update(CAS_STATUS, Map.of(
                "id", id,
                "expected", expected.name(),
                "target", target.name()
        ));
        return updated == 1;
    }

    public void updateCheckpoint(long id, String checkpointJson) {
        jdbc.update(UPDATE_CHECKPOINT, Map.of("id", id, "checkpoint", checkpointJson));
    }

    public void updateErrorDetails(long id, String errorDetailsJson) {
        jdbc.update(UPDATE_ERROR_DETAILS, Map.of("id", id, "errorDetails", errorDetailsJson));
    }

    public Optional<JobExecutionRow> findById(long id) {
        var rows = jdbc.query(FIND_BY_ID, Map.of("id", id), this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<JobExecutionRow> findByConnectionId(long connectionId, String status,
                                                     OffsetDateTime from, OffsetDateTime to,
                                                     int limit, long offset) {
        var params = buildFilterParams(connectionId, status, from, to);
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String sql = """
                SELECT id, connection_id, event_type, status, started_at, completed_at,
                       error_details::text, checkpoint::text, created_at
                FROM job_execution
                """ + buildWhereClause(status, from, to) + """
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """;

        return jdbc.query(sql, params, this::mapRow);
    }

    public long countByConnectionId(long connectionId, String status,
                                    OffsetDateTime from, OffsetDateTime to) {
        var params = buildFilterParams(connectionId, status, from, to);

        String sql = "SELECT count(*) FROM job_execution " + buildWhereClause(status, from, to);

        return jdbc.queryForObject(sql, params, Long.class);
    }

    private MapSqlParameterSource buildFilterParams(long connectionId, String status,
                                                    OffsetDateTime from, OffsetDateTime to) {
        var params = new MapSqlParameterSource().addValue("connectionId", connectionId);
        if (status != null) {
            params.addValue("status", status);
        }
        if (from != null) {
            params.addValue("from", from);
        }
        if (to != null) {
            params.addValue("to", to);
        }
        return params;
    }

    private String buildWhereClause(String status, OffsetDateTime from, OffsetDateTime to) {
        var sb = new StringBuilder("WHERE connection_id = :connectionId");
        if (status != null) {
            sb.append(" AND status = :status");
        }
        if (from != null) {
            sb.append(" AND created_at >= :from");
        }
        if (to != null) {
            sb.append(" AND created_at <= :to");
        }
        sb.append('\n');
        return sb.toString();
    }

    public boolean existsActiveForConnection(long connectionId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(EXISTS_ACTIVE_FOR_CONNECTION,
                        Map.of("connectionId", connectionId), Boolean.class)
        );
    }

    /**
     * Marks IN_PROGRESS jobs as STALE when started_at is older than the given threshold.
     *
     * @return number of rows affected
     */
    public int markStaleInProgress(OffsetDateTime threshold) {
        return jdbc.update(MARK_STALE_IN_PROGRESS, Map.of("threshold", threshold));
    }

    /**
     * Marks RETRY_SCHEDULED jobs as STALE when last_retry_at (from checkpoint)
     * is older than the given threshold. Falls back to started_at or created_at
     * if last_retry_at is absent.
     *
     * @return number of rows affected
     */
    public int markStaleRetryScheduled(OffsetDateTime threshold) {
        return jdbc.update(MARK_STALE_RETRY_SCHEDULED, Map.of("threshold", threshold));
    }

    private JobExecutionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return JobExecutionRow.builder()
                .id(rs.getLong("id"))
                .connectionId(rs.getLong("connection_id"))
                .eventType(rs.getString("event_type"))
                .status(rs.getString("status"))
                .startedAt(rs.getObject("started_at", OffsetDateTime.class))
                .completedAt(rs.getObject("completed_at", OffsetDateTime.class))
                .errorDetails(rs.getString("error_details"))
                .checkpoint(rs.getString("checkpoint"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .build();
    }
}
