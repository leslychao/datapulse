package io.datapulse.etl.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
                completed_at = CASE WHEN :target IN ('COMPLETED','COMPLETED_WITH_ERRORS','FAILED') THEN now() ELSE completed_at END
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
                  AND status IN ('PENDING', 'IN_PROGRESS', 'RETRY_SCHEDULED')
            )
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

    public boolean existsActiveForConnection(long connectionId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(EXISTS_ACTIVE_FOR_CONNECTION,
                        Map.of("connectionId", connectionId), Boolean.class)
        );
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
