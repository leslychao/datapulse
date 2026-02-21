package io.datapulse.etl.v1.execution;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EtlExecutionStateRepository {

  private final JdbcTemplate jdbcTemplate;

  public void insertExecution(String requestId, long accountId, MarketplaceEvent event,
                              LocalDate dateFrom, LocalDate dateTo, int totalSources) {
    jdbcTemplate.update("""
        insert into etl_execution(request_id, account_id, event, date_from, date_to, status, started_at,
          total_sources, completed_sources, failed_sources)
        values (?, ?, ?, ?, ?, ?, now(), ?, 0, 0)
        """, requestId, accountId, event.name(), dateFrom, dateTo, ExecutionStatus.NEW.name(), totalSources);
  }

  public void markExecutionInProgress(String requestId) {
    jdbcTemplate.update("update etl_execution set status = ? where request_id = ?",
        ExecutionStatus.IN_PROGRESS.name(), requestId);
  }

  public Optional<ExecutionRow> findExecution(String requestId) {
    return jdbcTemplate.query("select * from etl_execution where request_id = ?", EXECUTION_MAPPER, requestId)
        .stream().findFirst();
  }

  public void insertSourceStates(String requestId, MarketplaceEvent event, List<String> sourceIds, int maxAttempts) {
    for (String sourceId : sourceIds) {
      jdbcTemplate.update("""
          insert into etl_source_state(request_id, event, source_id, status, attempt, max_attempts)
          values (?, ?, ?, ?, 0, ?)
          """, requestId, event.name(), sourceId, SourceStateStatus.NEW.name(), maxAttempts);
    }
  }

  public Optional<SourceStateRow> findSourceState(String requestId, MarketplaceEvent event, String sourceId) {
    return jdbcTemplate.query("""
        select * from etl_source_state where request_id = ? and event = ? and source_id = ?
        """, SOURCE_MAPPER, requestId, event.name(), sourceId).stream().findFirst();
  }

  public boolean markSourceInProgress(String requestId, MarketplaceEvent event, String sourceId) {
    return jdbcTemplate.update("""
        update etl_source_state
        set status = ?
        where request_id = ? and event = ? and source_id = ? and status in (?, ?)
        """, SourceStateStatus.IN_PROGRESS.name(), requestId, event.name(), sourceId,
        SourceStateStatus.NEW.name(), SourceStateStatus.RETRY_SCHEDULED.name()) > 0;
  }

  public void markSourceCompleted(String requestId, MarketplaceEvent event, String sourceId) {
    jdbcTemplate.update("""
        update etl_source_state set status = ?, last_error_code = null, last_error_message = null, last_error_at = null
        where request_id = ? and event = ? and source_id = ?
        """, SourceStateStatus.COMPLETED.name(), requestId, event.name(), sourceId);
    jdbcTemplate.update("update etl_execution set completed_sources = completed_sources + 1 where request_id = ?", requestId);
  }

  public boolean scheduleRetry(String requestId, MarketplaceEvent event, String sourceId, String code,
                               String message, OffsetDateTime nextAttemptAt) {
    return jdbcTemplate.update("""
        update etl_source_state
        set attempt = attempt + 1, status = ?, next_attempt_at = ?, last_error_code = ?, last_error_message = ?, last_error_at = now()
        where request_id = ? and event = ? and source_id = ? and attempt < max_attempts
        """, SourceStateStatus.RETRY_SCHEDULED.name(), Timestamp.from(nextAttemptAt.toInstant()), code, message,
        requestId, event.name(), sourceId) > 0;
  }

  public void markSourceFailedTerminal(String requestId, MarketplaceEvent event, String sourceId, String code, String message) {
    jdbcTemplate.update("""
        update etl_source_state
        set status = ?, last_error_code = ?, last_error_message = ?, last_error_at = now()
        where request_id = ? and event = ? and source_id = ?
        """, SourceStateStatus.FAILED_TERMINAL.name(), code, message, requestId, event.name(), sourceId);
    jdbcTemplate.update("update etl_execution set failed_sources = failed_sources + 1 where request_id = ?", requestId);
  }

  public void resolveExecutionStatus(String requestId) {
    jdbcTemplate.update("""
        update etl_execution
        set status = case
            when failed_sources > 0 then ?
            when completed_sources = total_sources then ?
            else status
          end,
          ended_at = case
            when failed_sources > 0 or completed_sources = total_sources then now()
            else ended_at
          end
        where request_id = ?
        """, ExecutionStatus.FAILED.name(), ExecutionStatus.COMPLETED.name(), requestId);
  }

  private static final RowMapper<ExecutionRow> EXECUTION_MAPPER = (rs, rowNum) -> new ExecutionRow(
      rs.getString("request_id"),
      ExecutionStatus.valueOf(rs.getString("status")),
      rs.getInt("total_sources"),
      rs.getInt("completed_sources"),
      rs.getInt("failed_sources")
  );

  private static final RowMapper<SourceStateRow> SOURCE_MAPPER = (rs, rowNum) -> mapSource(rs);

  private static SourceStateRow mapSource(ResultSet rs) throws SQLException {
    return new SourceStateRow(
        rs.getString("request_id"),
        MarketplaceEvent.valueOf(rs.getString("event")),
        rs.getString("source_id"),
        SourceStateStatus.valueOf(rs.getString("status")),
        rs.getInt("attempt"),
        rs.getInt("max_attempts")
    );
  }

  public record ExecutionRow(String requestId, ExecutionStatus status, int totalSources,
                             int completedSources, int failedSources) {
    public boolean isTerminal() {
      return status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED;
    }
  }

  public record SourceStateRow(String requestId, MarketplaceEvent event, String sourceId,
                               SourceStateStatus status, int attempt, int maxAttempts) {
  }
}
