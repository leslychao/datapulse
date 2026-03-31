package io.datapulse.etl.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import io.datapulse.etl.domain.JobItemStatus;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JobItemRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT = """
            INSERT INTO job_item (job_execution_id, request_id, source_id, page_number,
                                  s3_key, record_count, content_sha256, byte_size, status, captured_at)
            VALUES (:jobExecutionId, :requestId, :sourceId, :pageNumber,
                    :s3Key, :recordCount, :contentSha256, :byteSize, :status, now())
            """;

    private static final String UPDATE_STATUS = """
            UPDATE job_item
            SET status = :status,
                processed_at = CASE WHEN :status IN ('PROCESSED', 'FAILED') THEN now() ELSE processed_at END
            WHERE id = :id
            """;

    private static final String MARK_EXPIRED = """
            UPDATE job_item SET status = 'EXPIRED' WHERE id IN (:ids)
            """;

    private static final String FIND_BY_JOB_EXECUTION_ID = """
            SELECT id, job_execution_id, request_id, source_id, page_number,
                   s3_key, record_count, content_sha256, byte_size, status,
                   captured_at, processed_at
            FROM job_item
            WHERE job_execution_id = :jobExecutionId
            ORDER BY page_number
            """;

    private static final String FIND_FOR_TIME_RETENTION = """
            SELECT id, job_execution_id, request_id, source_id, page_number,
                   s3_key, record_count, content_sha256, byte_size, status,
                   captured_at, processed_at
            FROM job_item
            WHERE status = 'PROCESSED'
              AND captured_at < :before
              AND s3_key LIKE :eventPattern
            ORDER BY captured_at
            LIMIT :batchSize
            """;

    private static final String FIND_EXCESS_STATE_ITEMS = """
            WITH ranked AS (
                SELECT ji.id, ji.s3_key, ji.request_id, ji.captured_at,
                       DENSE_RANK() OVER (
                           PARTITION BY SPLIT_PART(ji.s3_key, '/', 2)
                           ORDER BY ji.captured_at DESC
                       ) AS run_rank
                FROM job_item ji
                WHERE ji.status = 'PROCESSED'
                  AND ji.s3_key LIKE :eventPattern
            )
            SELECT r.id, ji.job_execution_id, ji.request_id, ji.source_id, ji.page_number,
                   ji.s3_key, ji.record_count, ji.content_sha256, ji.byte_size, ji.status,
                   ji.captured_at, ji.processed_at
            FROM ranked r
            JOIN job_item ji ON ji.id = r.id
            WHERE r.run_rank > :keepCount
            ORDER BY r.captured_at
            LIMIT :batchSize
            """;

    public long insert(JobItemRow row) {
        var keyHolder = new GeneratedKeyHolder();
        var params = new MapSqlParameterSource()
                .addValue("jobExecutionId", row.getJobExecutionId())
                .addValue("requestId", row.getRequestId())
                .addValue("sourceId", row.getSourceId())
                .addValue("pageNumber", row.getPageNumber())
                .addValue("s3Key", row.getS3Key())
                .addValue("recordCount", row.getRecordCount())
                .addValue("contentSha256", row.getContentSha256())
                .addValue("byteSize", row.getByteSize())
                .addValue("status", JobItemStatus.CAPTURED.name());

        jdbc.update(INSERT, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    public void updateStatus(long id, JobItemStatus status) {
        jdbc.update(UPDATE_STATUS, Map.of("id", id, "status", status.name()));
    }

    public void markExpired(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update(MARK_EXPIRED, Map.of("ids", ids));
    }

    public List<JobItemRow> findByJobExecutionId(long jobExecutionId) {
        return jdbc.query(FIND_BY_JOB_EXECUTION_ID,
                Map.of("jobExecutionId", jobExecutionId), this::mapRow);
    }

    public List<JobItemRow> findForTimeRetention(OffsetDateTime before, String eventPattern, int batchSize) {
        return jdbc.query(FIND_FOR_TIME_RETENTION, Map.of(
                "before", before,
                "eventPattern", eventPattern,
                "batchSize", batchSize
        ), this::mapRow);
    }

    public List<JobItemRow> findExcessStateItems(String eventPattern, int keepCount, int batchSize) {
        return jdbc.query(FIND_EXCESS_STATE_ITEMS, Map.of(
                "eventPattern", eventPattern,
                "keepCount", keepCount,
                "batchSize", batchSize
        ), this::mapRow);
    }

    private JobItemRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return JobItemRow.builder()
                .id(rs.getLong("id"))
                .jobExecutionId(rs.getLong("job_execution_id"))
                .requestId(rs.getString("request_id"))
                .sourceId(rs.getString("source_id"))
                .pageNumber(rs.getInt("page_number"))
                .s3Key(rs.getString("s3_key"))
                .recordCount(rs.getObject("record_count", Integer.class))
                .contentSha256(rs.getString("content_sha256"))
                .byteSize(rs.getLong("byte_size"))
                .status(rs.getString("status"))
                .capturedAt(rs.getObject("captured_at", OffsetDateTime.class))
                .processedAt(rs.getObject("processed_at", OffsetDateTime.class))
                .build();
    }
}
