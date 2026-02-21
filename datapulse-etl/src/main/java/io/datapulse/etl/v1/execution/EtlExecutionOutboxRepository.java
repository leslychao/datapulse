package io.datapulse.etl.v1.execution;

import io.datapulse.etl.v1.dto.EtlSourceExecution;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EtlExecutionOutboxRepository {

  private final JdbcTemplate jdbcTemplate;

  public void enqueueWait(EtlSourceExecution execution, String payloadJson, long ttlMillis) {
    jdbcTemplate.update("""
        insert into etl_outbox(type, aggregate_key, payload_json, ttl_millis, status)
        values ('WAIT_PUBLISH', ?, cast(? as jsonb), ?, ?)
        """, execution.requestId() + "|" + execution.event() + "|" + execution.sourceId(), payloadJson,
        ttlMillis, OutboxStatus.NEW.name());
  }

  public List<OutboxRow> lockBatch(int limit) {
    return jdbcTemplate.query("""
        select id, payload_json::text, ttl_millis from etl_outbox
        where status = ?
        order by id
        for update skip locked
        limit ?
        """, ROW_MAPPER, OutboxStatus.NEW.name(), limit);
  }

  public void markSent(long id) {
    jdbcTemplate.update("update etl_outbox set status = ? where id = ?", OutboxStatus.SENT.name(), id);
  }

  public void markFailed(long id) {
    jdbcTemplate.update("update etl_outbox set status = ? where id = ?", OutboxStatus.FAILED.name(), id);
  }

  private static final RowMapper<OutboxRow> ROW_MAPPER = (rs, rowNum) -> map(rs);

  private static OutboxRow map(ResultSet rs) throws SQLException {
    return new OutboxRow(rs.getLong("id"), rs.getString("payload_json"), rs.getLong("ttl_millis"));
  }

  public record OutboxRow(long id, String payloadJson, long ttlMillis) {}
}
