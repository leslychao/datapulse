package io.datapulse.etl.flow.repository;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EtlSourceStateRepo {

  private final JdbcTemplate jdbc;

  public EtlSourceStateRepo(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void ensureStatesExist(String requestId, EtlRunRequest req, List<RegisteredSource> sources) {
    int maxAttempts = req.executionPolicy().maxAttempts();

    for (RegisteredSource s : sources) {
      jdbc.update("""
        insert into etl_source_execution_state(
          request_id, event, source_id,
          status, attempt, max_attempts, next_attempt_at,
          last_error_code, last_error_msg,
          updated_at
        )
        values (?, ?, ?, 'NEW', 0, ?, null, null, null, now())
        on conflict (request_id, event, source_id) do nothing
      """,
          requestId,
          req.event().name(),
          s.sourceId(),
          maxAttempts
      );
    }
  }

  public SourceState load(String requestId, MarketplaceEvent event, String sourceId) {
    return jdbc.query("""
      select status, attempt, max_attempts, next_attempt_at
      from etl_source_execution_state
      where request_id=? and event=? and source_id=?
    """,
        rs -> {
          if (!rs.next()) {
            throw new IllegalStateException("Source state not found: " + requestId + "/" + event + "/" + sourceId);
          }
          return map(rs);
        },
        requestId, event.name(), sourceId
    );
  }

  private static SourceState map(ResultSet rs) throws SQLException {
    String status = rs.getString("status");
    int attempt = rs.getInt("attempt");
    int maxAttempts = rs.getInt("max_attempts");
    var ts = rs.getTimestamp("next_attempt_at");
    Instant nextAttemptAt = ts == null ? null : ts.toInstant();
    return new SourceState(status, attempt, maxAttempts, nextAttemptAt);
  }

  /**
   * Redelivery-safe:
   * - IN_PROGRESS => allow
   * - else CAS: NEW|RETRY_SCHEDULED -> IN_PROGRESS (attempt must match)
   */
  public boolean tryAcquireForProcessing(EtlSourceExecution exec, int expectedAttempt, String currentStatus) {
    if ("IN_PROGRESS".equals(currentStatus)) {
      return true;
    }

    int updated = jdbc.update("""
      update etl_source_execution_state
      set status='IN_PROGRESS', updated_at=now()
      where request_id=? and event=? and source_id=?
        and attempt=?
        and status in ('NEW','RETRY_SCHEDULED')
    """,
        exec.requestId(), exec.event().name(), exec.sourceId(),
        expectedAttempt
    );

    return updated == 1;
  }

  public void markCompleted(EtlSourceExecution exec, Instant now) {
    jdbc.update("""
      update etl_source_execution_state
      set status='COMPLETED',
          last_error_code=null,
          last_error_msg=null,
          updated_at=now()
      where request_id=? and event=? and source_id=?
        and status='IN_PROGRESS'
    """, exec.requestId(), exec.event().name(), exec.sourceId());
  }

  public void markFailedTerminal(EtlSourceExecution exec, String code, String msg) {
    jdbc.update("""
      update etl_source_execution_state
      set status='FAILED_TERMINAL',
          last_error_code=?,
          last_error_msg=?,
          updated_at=now()
      where request_id=? and event=? and source_id=?
        and status='IN_PROGRESS'
    """,
        code, msg,
        exec.requestId(), exec.event().name(), exec.sourceId()
    );
  }

  /**
   * IN_PROGRESS -> RETRY_SCHEDULED, attempt++, nextAttemptAt set.
   */
  public boolean casScheduleRetry(
      EtlSourceExecution exec,
      int expectedAttempt,
      int newAttempt,
      Instant nextAttemptAt,
      String reasonCode,
      String errorMessage
  ) {
    int updated = jdbc.update("""
      update etl_source_execution_state
      set status='RETRY_SCHEDULED',
          attempt=?,
          next_attempt_at=?,
          last_error_code=?,
          last_error_msg=?,
          updated_at=now()
      where request_id=? and event=? and source_id=?
        and attempt=?
        and status='IN_PROGRESS'
    """,
        newAttempt,
        nextAttemptAt,
        reasonCode,
        errorMessage,
        exec.requestId(), exec.event().name(), exec.sourceId(),
        expectedAttempt
    );
    return updated == 1;
  }

  public record SourceState(String status, int attempt, int maxAttempts, Instant nextAttemptAt) {
    public boolean isTerminal() {
      return "COMPLETED".equals(status) || "FAILED_TERMINAL".equals(status);
    }

    public boolean isRetryScheduled() {
      return "RETRY_SCHEDULED".equals(status);
    }
  }
}